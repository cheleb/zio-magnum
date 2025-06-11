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

def ztransactor(
    sqlLogger: SqlLogger = SqlLogger.Default,
    /** Customize the underlying JDBC Connections */
    connectionConfig: Connection => Unit = con => ()
): URLayer[DataSource, Transactor] =
  ZLayer(
    ZIO
      .service[DataSource]
      .map(ds => Transactor(ds, sqlLogger, connectionConfig))
  )

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
