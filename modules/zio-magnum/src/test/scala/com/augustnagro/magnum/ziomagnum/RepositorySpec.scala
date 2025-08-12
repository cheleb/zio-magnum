package com.augustnagro.magnum.ziomagnum

import zio.*

import javax.sql.DataSource

import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import com.augustnagro.magnum.SqlLogger
import scala.concurrent.duration.FiniteDuration
import zio.logging.backend.SLF4J

/** A trait that provides a PostgreSQL container for integration tests.
  */
trait RepositorySpec(init: String) {

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  given SqlLogger =
    Slf4jMagnumLogger.logSlowQueries(1.nanoseconds)

  private def postgres(): PostgreSQLContainer[Nothing] =
    val container: PostgreSQLContainer[Nothing] =
      PostgreSQLContainer("postgres")
        .withInitScript(init)
    container.start()
    container

  private def createDataSource(
      container: PostgreSQLContainer[Nothing]
  ): DataSource =
    val dataSource = new PGSimpleDataSource()
    dataSource.setUrl(container.getJdbcUrl)
    dataSource.setUser(container.getUsername)
    dataSource.setPassword(container.getPassword)
    dataSource

  val testDataSouurceLayer = ZLayer {
    for {
      container <- ZIO.acquireRelease(ZIO.attempt(postgres()))(container =>
        ZIO.attempt(container.stop()).ignoreLogged
      )
    } yield createDataSource(container)
  }
}
