package com.augustnagro.magnum.ziomagnum

import zio.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.augustnagro.magnum.*
import javax.sql.*

import zio.stream.ZStream
import java.sql.*
import zio.Exit.Success
import zio.Exit.Failure
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS
import scala.concurrent.duration.SECONDS
import scala.language.implicitConversions

/** ZIO Magnum is a ZIO-based library for working with SQL databases in a
  * functional way.
  */

/** Default SQL logger that uses SLF4J. */
implicit val sqlLogger: SqlLogger =
  Slf4jMagnumLogger.Default

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
private def withConnection[A](
    op: Connection ?=> Task[A]
): DataSource ?=> Task[A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(using connection)
    case None             =>
      ZIO.scoped(
        fiberRefConnection(false).flatMap(connection => op(using connection))
      )
  }

/** Provides a database connection for the duration of the operation.
  */
private def withDbConnection[A](
    op: DbCon ?=> Task[A]
)(using sqlLogger: SqlLogger, dataSource: DataSource): Task[A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(using DbCon(connection, sqlLogger))
    case None             =>
      ZIO.scoped(
        fiberRefConnection(false).flatMap(connection =>
          op(using DbCon(connection, sqlLogger))
        )
      )
  }

/** Provides a database connection for the duration of the operation. This is
  * similar to `withDbConnection`, but it uses a `Scope` to ensure that the
  * connection is closed when the operation is done. This is useful for
  * operations that need to be run in a `ZIO.scoped` block, such as streaming
  * queries.
  * @param op
  * @return
  */
private def withScopedConnection[A](
    op: Connection ?=> RIO[Scope, A]
): DataSource ?=> RIO[Scope, A] =
  currentConnection.get.flatMap {
    case Some(connection) => op(using connection)
    case None             =>
      fiberRefConnection(false).flatMap(db => op(using db))
  }

/** Prepares a statement for the given `Frag` using the provided connection.
  *
  * @param connection
  * @param frag
  * @return
  */
private def prepareStatement(
    connection: Connection,
    frag: Frag
): RIO[Scope, PreparedStatement] = for
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
)(using dataSource: DataSource): RIO[Scope, Connection] =
  for {
    connection <- scopedBestEffort(
      ZIO.logDebug("Creating new connection") *>
        // Use `ZIO.attemptBlocking` to ensure that the blocking operation is run on a
        // blocking thread pool, preventing it from blocking the ZIO runtime.
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
      case Success(_) =>
        ZIO
          .attemptBlocking(connection.commit())
          .onDoneCause(
            th =>
              ZIO.logWarningCause(
                s"Transaction commit failed",
                th
              ),
            _ =>
              ZIO.logDebug(
                "Transaction committed successfully"
              )
          )
      case Failure(cause) =>
        ZIO
          .attemptBlocking(connection.rollback())
          .onDoneCause(
            th =>
              ZIO.logWarningCause(
                s"Transaction rolled back due to failure: ${cause.prettyPrint}",
                th
              ),
            _ =>
              ZIO.logDebug(
                s"Transaction rolled back successfully after failure: ${cause.prettyPrint}"
              )
          )
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
          .attempt(
            ZIO.logError(s"close() of resource failed: ${e.getMessage()}")
          )
          .ignore
      )
      .ignore
  )

/** Creates a ZLayer that provides a DataSource using HikariCP.
  *
  * @param jdbcUrl
  *   The JDBC URL for the database.
  * @param username
  *   The username to connect to the database.
  * @param password
  *   The password to connect to the database.
  * @return
  */
def dataSourceLayer(jdbcUrl: String, username: String, password: String) =
  customDataSourceLayer(jdbcUrl, username, password)(identity)

/** Creates a ZLayer that provides a DataSource using HikariCP.
  *
  * @param jdbcUrl
  *   The JDBC URL for the database.
  * @param username
  *   The username to connect to the database.
  * @param password
  *   The password to connect to the database.
  * @param customize
  *   function to customize the HikariConfig
  * @return
  */
def customDataSourceLayer(jdbcUrl: String, username: String, password: String)(
    customize: HikariConfig => HikariConfig
) =
  ZLayer(ZIO.fromAutoCloseable {
    ZIO.attemptBlockingIO {
      val config = HikariConfig()
      config.setJdbcUrl(jdbcUrl)
      config.setUsername(username)
      config.setPassword(password)
      HikariDataSource(customize(config))
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
def transaction[A](
    op: Connection ?=> Task[A]
)(using dataSource: DataSource): Task[A] =
  currentConnection.get.flatMap {
    case Some(connection) =>
      ZIO.scoped:
        // Get the current value of auto-commit
        for
          _ <- ZIO.logDebug("Disabling auto-commit for transaction")
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
                  *> ZIO.logDebug("Transaction committed successfully.")
              case (_, Failure(cause)) =>
                ZIO.blocking(ZIO.succeed(connection.rollback()))
                  *>
                    ZIO
                      .attemptBlocking(connection.setAutoCommit(prevAutoCommit))
                      .orDie
                    *> ZIO.logWarning(
                      s"Transaction rolled back due to failure: ${cause.prettyPrint}"
                    )
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
extension [A](query: Query[A])(using reader: DbCodec[A], sqlLogger: SqlLogger)

  /** An ZIO that:
    *   - prepares the statement
    *   - runs the query
    *   - returns a vector of results.
    *
    * @param connection
    *   The database connection to use.
    * @return
    */
  private def toZIO(using connection: Connection): Task[Vector[A]] = ZIO.scoped:
    (for
      ps <- prepareStatement(connection, query.frag)
      (execTime, rs) <- ZIO
        .fromAutoCloseable(
          ZIO.attemptBlocking(ps.executeQuery())
        )
        .timed
        .tapError(e =>
          ZIO.logErrorCause(
            s"Failed to execute query: ${e.getMessage()}",
            Cause.fail(e)
          )
        )
    yield (execTime, reader.read(rs)))
      .map((execTime, result) =>
        sqlLogger.log(
          SqlSuccessEvent(
            query.frag.sqlString,
            query.frag.params,
            FiniteDuration(execTime.toMillis(), MILLISECONDS)
          )
        )
        result
      )

  /** Runs the query and returns a vector of results.
    *
    * @param reader
    * @param R
    * @return
    */
  private def zrun: DataSource ?=> Task[Vector[A]] =
    withConnection:
      toZIO

  /** Runs the query and returns a stream of results.
    * @param A
    *   the type of the results.
    * @param fetchSize
    *   the number of rows to fetch at a time from the database.
    */
  private def zstream(
      fetchSize: Int
  )(using DataSource): ZStream[Any, Throwable, A] =
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
  )(using
      connection: Connection
  ): RIO[Scope, ResultSetIterator[A]] =
    for
      ps <- prepareStatement(connection, query.frag)

      _ = ps.setFetchSize(fetchSize)

      rs <- ZIO.fromAutoCloseable(
        ZIO.attemptBlocking(ps.executeQuery())
      )
    yield ResultSetIterator(
      rs,
      query.frag,
      reader,
      sqlLogger
    )

/** Provides a ZIO-based update interface for the given `Update`.
  */
extension (update: Update)(using sqlLogger: SqlLogger)

  /** An ZIO that:
    *   - prepares the statement
    *   - runs the update
    *   - returns the number of rows affected.
    *
    * @param connection
    *   The database connection to use.
    * @return
    */
  private def toZIO(using connection: Connection): Task[Int] = ZIO.scoped:
    (for
      ps <- ZIO.fromAutoCloseable(
        ZIO.attemptBlocking(
          connection.prepareStatement(update.frag.sqlString)
        )
      )
      _ = update.frag.writer.write(ps, 1)
      _ <- ZIO.logDebug(
        s"Executing update: ${update.frag.sqlString} with params: ${update.frag.params}"
      )
    yield ps.executeUpdate()).timed
      .map((execTime, result) =>
        sqlLogger.log(
          SqlSuccessEvent(
            update.frag.sqlString,
            update.frag.params,
            FiniteDuration(execTime.toSeconds(), SECONDS)
          )
        )
        result
      )

  def zrun: DataSource ?=> Task[Int] = withConnection:
    toZIO

/** Provides a ZIO-based query interface for the given `Frag`.
  */
extension (frag: Frag)(using SqlLogger, DataSource)
  /** Runs the query and returns a vector of results.
    *
    * @param A
    * @return
    */
  def zQuery[A: DbCodec]: Task[Vector[A]] =
    frag.query[A].zrun

  /** Runs the update and returns the number of rows affected.
    *
    * @return
    */
  def zUpdate: Task[Int] =
    frag.update.zrun

  /** Runs the query and returns a stream of results.
    *
    * @param A
    *   the type of the results.
    * @param fetchSize
    *   the number of rows to fetch at a time from the database.
    */
  def zStream[A: DbCodec](
      fetchSize: Int = 10
  ): ZStream[Any, Throwable, A] =
    frag.query[A].zstream(fetchSize)

/** Provides a ZIO-based query interface for the given `ImmutableRepo`.
  */
extension [R <: DataSource, A, K](repo: ImmutableRepo[A, K])(using SqlLogger)

  /** Counts the number of rows in the table.
    *
    * @return
    */
  def zcount: DataSource ?=> Task[Long] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.count

  /** Checks if a row with the given id exists in the table.
    *
    * @param id
    *   The id of the row to check.
    * @return
    */
  def zExistsById(
      id: K
  ): DataSource ?=> Task[Boolean] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.existsById(id)

  /** Finds a row by its id.
    */
  def zFindById(
      id: K
  ): DataSource ?=> Task[Option[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findById(id)

  /** Finds all rows in the table.
    *
    * @return
    */
  def zFindAll: DataSource ?=> Task[Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAll

  /** Finds all rows that match the given spec.
    *
    * @param spec
    *   The specification to use for filtering the results.
    * @return
    */
  def zFindAll(spec: Spec[A]): DataSource ?=> Task[Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAll(spec)

  /** Finds all rows with the given ids.
    *
    * @param ids
    *   The set of ids to find.
    * @return
    */
  def zFindAllById(
      ids: Set[K]
  ): DataSource ?=> Task[Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.findAllById(ids)

/** Provides a ZIO-based query interface for the given `Repo`.
  */
extension [EC, A, K](repo: Repo[EC, A, K])(using SqlLogger)

  /** Deletes a row by its id.
    *
    * @param id
    *   The id of the row to delete.
    * @return
    */
  def zDeleteById(
      id: K
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteById(id)

  /** Deletes all rows in the table.
    *
    * @param set
    *   The set of elements to delete.
    * @return
    */
  def zDeleteAll(
      set: Set[A]
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteAll(set)

  /** Deletes all rows with the given ids.
    *
    * @param ids
    *   The set of ids to delete.
    * @return
    */
  def zDeleteAllById(
      ids: Set[K]
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.deleteAllById(ids)

  /** Inserts a new row into the table.
    *
    * @param a
    *   The element to insert.
    * @return
    */
  def zInsert(
      a: EC
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insert(a)

  /** Inserts a new row into the table and returns the inserted element.
    *
    * @param a
    *   The element to insert.
    * @return
    *   the inserted element.
    */
  def zInsertReturning(
      a: EC
  ): DataSource ?=> Task[A] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertReturning(a)

  /** Inserts all elements in the set into the table.
    *
    * @param set
    *   The set of elements to insert.
    * @return
    *   The inserted elements.
    */
  def zInsertAll(
      set: Set[EC]
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertAll(set)

  /** Inserts all elements in the set into the table
    *
    * @param set
    *   The set of elements to insert.
    * @return
    *   The inserted elements.
    */
  def zInsertAllReturning(
      set: Set[EC]
  ): DataSource ?=> Task[Vector[A]] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.insertAllReturning(set)

  /** Truncates the table, removing all rows.
    *
    * @return
    */
  def zTruncate(): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.truncate()

  /** Updates an existing row in the table.
    *
    * @param a
    *   The element to update.
    * @return
    */
  def zUpdate(
      a: A
  ): DataSource ?=> Task[Unit] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.update(a)

  /** Updates all elements in the set.
    *
    * @param set
    *   The set of elements to update.
    * @return
    */
  def zUpdateAll(
      set: Set[A]
  ): DataSource ?=> Task[BatchUpdateResult] =
    withDbConnection:
      ZIO.attemptBlocking:
        repo.updateAll(set)

/** Converts a `Duration` to a `FiniteDuration`.
  *
  * Magnum uses `FiniteDuration` for logging long queries.
  */
given Conversion[Duration, FiniteDuration] with
  def apply(d: Duration): FiniteDuration =
    d.asFiniteDuration
