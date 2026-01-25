package com.augustnagro.magnum.ziomagnum

import zio.*

import javax.sql.DataSource

import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

import zio.logging.backend.SLF4J
import scala.language.implicitConversions

/** A trait that provides a PostgreSQL container for integration tests.
  */
trait RepositorySpec(init: String) {

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

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
