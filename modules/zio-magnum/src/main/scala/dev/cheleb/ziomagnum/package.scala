package com.augustnagro.magnum.ziomagnum

import zio.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.augustnagro.magnum.*
import javax.sql.*

import zio.stream.ZStream
import scala.util.Using
import scala.util.Try
import java.io.IOException
import java.sql.*
import zio.Exit.Success
import zio.Exit.Failure
import scala.annotation.targetName

/** ZIO Magnum is a ZIO-based library for working with SQL databases in a
  * functional way.
  */

/** Current database connection for the fiber */
private val currentConnection: FiberRef[Option[Connection]] =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(
        zio.Scope.global
          .extend(FiberRef.make(Option.empty[Connection]))
      )
      .getOrThrow()
  }

/*
 * Use the connection from the fiberRef if it exists.
 * Otherwise create a new
 * connection using the DataSource, put it in the fiberRef, and remove it when done.
 */
private def withConnection[R <: DataSource, A](
    op: Connection => ZIO[R, Throwable, A]
): ZIO[R, Throwable, A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(connection)
    case None =>
      ZIO.scoped(fiberRefConnection(false).flatMap(db => op(db)))
  }

private def withDbConnection[R <: DataSource, A](
    op: DbCon ?=> ZIO[R, Throwable, A]
): ZIO[R, Throwable, A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(using DbCon(connection, SqlLogger.Default))
    case None =>
      ZIO.scoped(
        fiberRefConnection(false).flatMap(connection =>
          op(using DbCon(connection, SqlLogger.Default))
        )
      )
  }

private def withScopedConnection[R <: DataSource & Scope, A](
    op: Connection ?=> ZIO[R, Throwable, A]
): ZIO[R, Throwable, A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(using connection)
    case None =>
      fiberRefConnection(false).flatMap(db => op(using db))
  }

private def prepareStatement(
    connection: Connection,
    frag: Frag
): ZIO[Scope, Throwable, java.sql.PreparedStatement] = for
  ps <- ZIO
    .fromAutoCloseable(
      ZIO.attemptBlockingIO(
        connection.prepareStatement(frag.sqlString)
      )
    )
  _ = frag.writer.write(ps, 1)
yield ps

/** Creates a new connection using the current DataSource and sets in the
  * fiberRef.
  *
  * Registers a finalizer to commit/rollback the transaction if it was started.
  *
  * @param tx
  *   Whether the connection is for a transaction
  * @return
  */
private def fiberRefConnection(
    tx: Boolean
): ZIO[DataSource & Scope, Throwable, Connection] =
  for {
    dataSource <- ZIO.service[DataSource]
    connection <- scopedBestEffort(
      ZIO.attemptBlocking(dataSource.getConnection)
    )
    // Disable auto-commit since we need to be able to roll back. Once everything is done, set it
    // to whatever the previous value was.
    _ <- ZIO.when(tx)(ZIO.attemptBlocking(connection.setAutoCommit(false)))

    _ <- ZIO.acquireRelease(currentConnection.set(Some(connection))) { _ =>
      // Note. We are failing the fiber if auto-commit reset fails. For some circumstances this may be too aggresive.
      // If the connection pool e.g. Hikari resets this property for a recycled connection anyway doing it here
      // might not be necessary
      currentConnection.set(None)
    }
    // Once the `use` of this outer-Scoped is done, rollback the connection if needed
    _ <- ZIO.when(tx)(ZIO.addFinalizerExit {
      case Success(_) => ZIO.blocking(ZIO.succeed(connection.commit()))
      case Failure(cause) =>
        ZIO.blocking(ZIO.succeed(connection.rollback()))
    })
  } yield connection

/** Runs the effect and ensures that the resource is closed when done.
  *
  * @param effect
  * @return
  */
private def scopedBestEffort[R, E, A <: AutoCloseable](
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

/** Creates a ZLayer that provides a DataSource using HikariCP.
  *
  * @param jdbcUrl
  * @param username
  * @param password
  * @return
  */
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

/** Runs the operation in a transaction.
  *
  * If there is already a connection set on the fiber ref.
  *   - If auto-commit is enabled, we need to disable it and set it back to the
  *     previous value after the operation is done.
  *   - If auto-commit is disabled, we can just run the operation using the
  *     existing connection hence same transaction.
  * else
  *   - Create a new connection using the DataSource in the fiber
  *   - Transaction will be committed or rolled back depending on the
  *     success/failure of the operation.
  *   - Will be removed from the fiber ref once the operation is done
  *
  * @param op
  * @return
  */
def transaction[R <: DataSource, A](
    op: Connection ?=> ZIO[R, Throwable, A]
): ZIO[R, Throwable, A] =
  currentConnection.get.flatMap {
    case Some(connection) =>
      ZIO.scoped:
        // Get the current value of auto-commit
        for
          prevAutoCommit <- ZIO.attemptBlocking(connection.getAutoCommit)
          // Disable auto-commit since we need to be able to roll back. Once everything is done, set it
          // to whatever the previous value was.
          _ <- ZIO.when(prevAutoCommit)(
            ZIO.acquireReleaseExit(
              ZIO.attemptBlocking(connection.setAutoCommit(false))
            ) {

              case (_, Success(_)) =>
                ZIO.blocking(ZIO.succeed(connection.commit()))
                  *> ZIO
                    .attemptBlocking(connection.setAutoCommit(prevAutoCommit))
                    .orDie
              case (_, Failure(cause)) =>
                ZIO.blocking(ZIO.succeed(connection.rollback()))
                  *>
                    ZIO
                      .attemptBlocking(connection.setAutoCommit(prevAutoCommit))
                      .orDie
            }
          )

          res <- op(using connection)
        yield res

    case None =>
      ZIO.scoped:
        fiberRefConnection(true).flatMap(db => op(using db))
  }

/** Provides a ZIO-based query interface for the given `Query[A]`.
  */
extension [A](query: Query[A])(using reader: DbCodec[A])

  /** An ZIO that:
    *   - prepares the statement
    *   - runs the query
    *   - returns a vector of results.
    *
    * @param connection
    *   The database connection to use.
    * @return
    */
  private def toZIO(connection: Connection) = ZIO.scoped:
    for
      ps <- prepareStatement(connection, query.frag)
      rs <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(ps.executeQuery())
      )
    yield reader.read(rs)

  /** Runs the query and returns a vector of results.
    *
    * @param reader
    * @param R
    * @return
    */
  private def zrun[R <: DataSource]: ZIO[R, Throwable, Vector[A]] =
    withConnection:
      toZIO

  /** Runs the query and returns a stream of results.
    * @param A
    *   the type of the results.
    * @param fetchSize
    *   the number of rows to fetch at a time from the database.
    */
  private def zstream(fetchSize: Int): ZStream[DataSource, Throwable, A] =
    ZStream.unwrapScoped(
      withScopedConnection:
        ziterator(fetchSize)
          .map: it =>
            ZStream.fromIterator(it)
    )

  /** Creates a `ResultSetIterator` for the given `Query[A]`.
    *
    * @param fetchSize
    *   the number of rows to fetch at a time from the database.
    * @return
    */
  private def ziterator(
      fetchSize: Int
  )(using connection: Connection): ZIO[Scope, Throwable, ResultSetIterator[A]] =
    for
      ps <- prepareStatement(connection, query.frag)

      _ = ps.setFetchSize(fetchSize)

      rs <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(ps.executeQuery())
      )
    yield ResultSetIterator(
      rs,
      query.frag,
      reader,
      SqlLogger.Default
    )

/** Provides a ZIO-based update interface for the given `Update`.
  */
extension (update: Update)

  /** An ZIO that:
    *   - prepares the statement
    *   - runs the update
    *   - returns the number of rows affected.
    *
    * @param connection
    *   The database connection to use.
    * @return
    */
  private def toZIO(connection: Connection) = ZIO.scoped:
    for
      ps <- ZIO.fromAutoCloseable(
        ZIO.attemptBlockingIO(
          connection.prepareStatement(update.frag.sqlString)
        )
      )
      _ = update.frag.writer.write(ps, 1)
    yield ps.executeUpdate()

  def zrun[R <: DataSource]: ZIO[R, Throwable, Int] = withConnection:
    toZIO

/** Provides a ZIO-based query interface for the given `Frag`.
  */
extension (frag: Frag)
  /** Runs the query and returns a vector of results.
    *
    * @param A
    * @return
    */
  def zQuery[A: DbCodec]: ZIO[DataSource, Throwable, Vector[A]] =
    frag.query[A].zrun

  /** Runs the update and returns the number of rows affected.
    *
    * @return
    */
  def zUpdate: ZIO[DataSource, Throwable, Int] =
    frag.update.zrun

  /** Runs the query and returns a stream of results.
    *
    * @param A
    *   the type of the results.
    * @param fetchSize
    *   the number of rows to fetch at a time from the database.
    */
  def zStream[A: DbCodec](fetchSize: Int = 10) =
    frag.query[A].zstream(fetchSize)

/** Provides a ZIO-based query interface for the given `ImmutableRepo`.
  */
extension [R <: DataSource, A, K](repo: ImmutableRepo[A, K])

  /** An ZIO that:
    *   - counts the number of rows in the table.
    *
    * @return
    */
  def zcount: ZIO[R, Throwable, Long] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.count

  /** An ZIO that:
    *   - checks if a row with the given id exists in the table.
    *
    * @param id
    *   The id of the row to check.
    * @return
    */
  def zExistsById(
      id: K
  ): ZIO[R, Throwable, Boolean] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.existsById(id)

  /** An ZIO that:
    *   - finds a row by its id.
    */
  def zFindById(
      id: K
  ): ZIO[R, Throwable, Option[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findById(id)

  /** An ZIO that:
    *   - finds all rows in the table.
    *
    * @return
    */
  def zFindAll: ZIO[R, Throwable, Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAll

  /** An ZIO that:
    *   - finds all rows that match the given spec.
    *
    * @param spec
    *   The specification to use for filtering the results.
    * @return
    */
  def zFindAll(spec: Spec[A]): ZIO[R, Throwable, Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAll(spec)

  /** An ZIO that:
    *   - finds all rows with the given ids.
    *
    * @param ids
    *   The set of ids to find.
    * @return
    */
  def zFindAllById(
      ids: Set[K]
  ): ZIO[R, Throwable, Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAllById(ids)

/** Provides a ZIO-based query interface for the given `Repo`.
  */
extension [R <: DataSource, EC, A, K](repo: Repo[EC, A, K])

  /** An ZIO that:
    *   - counts the number of rows in the table.
    *
    * @return
    */
  def zDeleteById(
      id: K
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteById(id)

  /** An ZIO that:
    *   - deletes all rows in the table.
    *
    * @param set
    *   The set of elements to delete.
    * @return
    */
  def zDeleteAll(
      set: Set[A]
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteAll(set)

  /** An ZIO that:
    *   - deletes all rows with the given ids.
    *
    * @param ids
    *   The set of ids to delete.
    * @return
    */
  def zDeleteAllById(
      ids: Set[K]
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteAllById(ids)

  /** An ZIO that:
    *   - inserts a new row into the table.
    *
    * @param a
    *   The element to insert.
    * @return
    */
  def zInsert(
      a: EC
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insert(a)

  /** An ZIO that:
    *   - inserts a new row into the table and returns the inserted element.
    *
    * @param a
    *   The element to insert.
    * @return
    *   the inserted element.
    */
  def zInsertReturning(
      a: EC
  ): ZIO[R, Throwable, A] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertReturning(a)

  /** An ZIO that:
    *   - inserts all elements in the set into the table.
    *
    * @param set
    *   The set of elements to insert.
    * @return
    *   The inserted elements.
    */
  def zInsertAll(
      set: Set[EC]
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertAll(set)

  /** An ZIO that:
    *   - inserts all elements in the set into the table and returns the
    *     inserted elements.
    *
    * @param set
    *   The set of elements to insert.
    * @return
    *   The inserted elements.
    */
  def zInsertAllReturning(
      set: Set[EC]
  ): ZIO[R, Throwable, Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertAllReturning(set)

  /** An ZIO that:
    *   - updates an existing row in the table.
    *
    * @param a
    *   The element to update.
    * @return
    */
  def zUpdate(
      a: A
  ): ZIO[R, Throwable, Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.update(a)

  /** An ZIO that:
    *   - updates all elements in the set.
    *
    * @param set
    *   The set of elements to update.
    * @return
    */
  def zUpdateAll(
      set: Set[A]
  ): ZIO[R, Throwable, BatchUpdateResult] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.updateAll(set)
