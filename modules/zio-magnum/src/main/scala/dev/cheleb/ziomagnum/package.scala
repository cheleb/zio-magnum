package com.augustnagro.magnum.ziomagnum

import zio.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.augustnagro.magnum.*
import javax.sql.DataSource

import zio.stream.ZStream
import scala.util.Using
import scala.util.Try
import java.io.IOException

def dbConLayer(
): ZLayer[Scope & DataSource, Throwable, DbCon] =
  ZLayer {
    for
      ds <- ZIO.service[DataSource]
      con <- ZIO.fromAutoCloseable(ZIO.attempt(ds.getConnection()))
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

extension [A](query: Query[A])
  def zrun: ZIO[DbCon, Throwable, Vector[A]] =
    for
      dbConn <- ZIO.service[DbCon]
      res = query.run()(using dbConn)
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
