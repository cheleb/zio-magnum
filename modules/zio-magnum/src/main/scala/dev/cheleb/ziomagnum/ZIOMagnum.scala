package dev.cheleb.ziomagnum

import zio.*
import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*
import scala.util.Using

@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class User(id: Int, name: String) derives DbCodec

val userRepo = ImmutableRepo[User, Int]

object ZIOMagnum extends ZIOAppDefault:

  val program = for {

    given DbCon <- ZIO.service[DbCon]

    ls <- sql"SELECT * FROM \"user\""
      .query[User]
      .zrun
    _ <- ZIO.debug(s"Users: ${ls.mkString(", ")}")
    zs = sql"SELECT * FROM \"user\""
      .query[User]
      .zstream()
    _ <- zs.runForeach(user => ZIO.debug(s"User from stream: $user"))

    count = userRepo.count

    _ <- ZIO.debug(s"User count: $count")

  } yield ()

  def run: ZIO[ZIOAppArgs, Any, Any] =
    program.provide(
      Scope.default,
      dataSourceLayer(
        "jdbc:postgresql://localhost:5432/cheleb",
        "test",
        "test"
      ),
      dbConLayer()
    )
