package com.augustnagro.magnum.ziomagnum

import zio.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.augustnagro.magnum.*
import javax.sql.DataSource

import zio.stream.ZStream
import scala.util.Using
import scala.util.Try
import java.io.IOException
import java.sql.Connection
import zio.Exit.Success
import zio.Exit.Failure
import scala.annotation.targetName

val currentConnection: FiberRef[Option[Connection]] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(
        zio.Scope.global
          .extend(FiberRef.make(Option.empty[java.sql.Connection]))
      )
      .getOrThrow()
  }

def scopedBestEffort[R, E, A <: AutoCloseable](
    effect: ZIO[R, E, A]
): ZIO[R & Scope, E, A] =
  ZIO.acquireRelease(effect)(resource =>
    ZIO
      .attemptBlocking(resource.close())
      .tapError(e =>
        ZIO
          .attempt(ZIO.logError(s"close() of resource failed"))
          .ignore
      )
      .ignore
  )

def dbConLayer(
): ZLayer[Scope & DataSource, Throwable, DbCon] =
  ZLayer {
    for
      _ <- ZIO.logDebug("Creating DbCon layer")
      ds <- ZIO.service[DataSource]
      con <- ZIO
        .fromAutoCloseable(ZIO.attempt(ds.getConnection()))
      sqlLogger = SqlLogger.Default
    yield DbCon(con, sqlLogger)
  }

def dataSourceLayer(jdbcUrl: String, username: String, password: String) =
  ZLayer(ZIO.fromAutoCloseable {
    ZIO.attemptBlockingIO {
      val config = HikariConfig()
      config.setJdbcUrl(jdbcUrl)
      config.setUsername(username)
      config.setPassword(password)
      HikariDataSource(config)
    }
  })

def transaction[R <: DataSource, A](
    op: ZIO[R, Throwable, A]
): ZIO[R, Throwable, A] = {
  ZIO.blocking(currentConnection.get.flatMap {
    // We can just return the op in the case that there is already a connection set on the fiber ref
    // because the op is execute___ which will lookup the connection from the fiber ref via onConnection/onConnectionStream
    // This will typically happen for nested transactions e.g. transaction(transaction(a *> b) *> c)
    case Some(connection) => op
    case None =>
      val connection = for {
        env <- ZIO.service[DataSource]
        connection <- scopedBestEffort(
          ZIO.attemptBlocking(env.getConnection)
        )
        // Get the current value of auto-commit
        prevAutoCommit <- ZIO.attemptBlocking(connection.getAutoCommit)
        // Disable auto-commit since we need to be able to roll back. Once everything is done, set it
        // to whatever the previous value was.
        _ <- ZIO.acquireRelease(
          ZIO.attemptBlocking(connection.setAutoCommit(false))
        ) { _ =>
          ZIO.attemptBlocking(connection.setAutoCommit(prevAutoCommit)).orDie
        }
        _ <- ZIO.acquireRelease(currentConnection.set(Some(connection))) { _ =>
          // Note. We are failing the fiber if auto-commit reset fails. For some circumstances this may be too aggresive.
          // If the connection pool e.g. Hikari resets this property for a recycled connection anyway doing it here
          // might not be necessary
          currentConnection.set(None)
        }
        // Once the `use` of this outer-Scoped is done, rollback the connection if needed
        _ <- ZIO.addFinalizerExit {
          case Success(_) => ZIO.blocking(ZIO.succeed(connection.commit()))
          case Failure(cause) =>
            ZIO.blocking(ZIO.succeed(connection.rollback()))
        }
      } yield ()

      ZIO.scoped(connection *> op)
  })
}

extension [A](query: Query[A])

  def zrun: ZIO[Scope & DbCon, Throwable, Vector[A]] =
    for
      dbCon <- ZIO.service[DbCon]

      ps <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(
          dbCon.connection.prepareStatement(query.frag.sqlString)
        )
      )
      _ = query.frag.writer.write(ps, 1)

      rs <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(ps.executeQuery())
      )
      res = query.reader.read(rs)
    yield res

  def zstream(fetchSize: Int = 10): ZStream[DbCon, Throwable, A] =
    ZStream.unwrapScoped(
      for
        dbConn <- ZIO.service[DbCon]
        it <- ziterator(fetchSize)(using dbConn)
      yield ZStream
        .fromIterator(it)
    )
  private def ziterator(
      fetchSize: Int
  )(using dbCon: DbCon): ZIO[Scope, IOException, ResultSetIterator[A]] =
    for
      ps <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(
          dbCon.connection.prepareStatement(query.frag.sqlString)
        )
      )
      _ = ps.setFetchSize(fetchSize)
      _ = query.frag.writer.write(ps, 1)

      rs <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(ps.executeQuery())
      )
    yield ResultSetIterator(
      rs,
      query.frag,
      query.reader,
      dbCon.sqlLogger
    )

extension (update: Update)
  @targetName("update")
  def zrun: ZIO[Scope & DbCon, Throwable, Int] =
    for
      dbCon <- ZIO.service[DbCon]

      ps <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(
          dbCon.connection.prepareStatement(update.frag.sqlString)
        )
      )
      _ = update.frag.writer.write(ps, 1)

      res = update.run()(using dbCon)
    yield res
