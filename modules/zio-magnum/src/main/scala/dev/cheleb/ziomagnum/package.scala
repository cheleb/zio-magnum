package com.augustnagro.magnum.ziomagnum

import zio.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.augustnagro.magnum.*
import javax.sql.DataSource
import scala.annotation.targetName

def dbConLive(
): ZLayer[Scope & DataSource, Throwable, DbCon] =
  ZLayer {
    for
      ds <- ZIO.service[DataSource]
      con <- ZIO.fromAutoCloseable(ZIO.attempt(ds.getConnection()))
      sqlLogger = SqlLogger.Default
    yield DbCon(con, sqlLogger)
  }

def createDataSource(jdbcUrl: String, username: String, password: String) =
  ZLayer(ZIO.fromAutoCloseable {
    ZIO.attemptBlockingIO {
      val config = HikariConfig()
      config.setJdbcUrl(jdbcUrl)
      config.setUsername(username)
      config.setPassword(password)
      HikariDataSource(config)
    }
  })

extension [A](query: Query[A])
  def zioRun: ZIO[DbCon, Throwable, Vector[A]] =
    for
      dbConn <- ZIO.service[DbCon]
      res = query.run()(using dbConn)
    yield res
