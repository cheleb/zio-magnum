package dev.cheleb.ziomagnum

import zio.*
import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*

case class User(id: Int, name: String) derives DbCodec

object ZIOMagnum extends ZIOAppDefault:

  val program = for {

    ls <- sql"SELECT * FROM \"user\""
      .query[User]
      .zioRun
    _ <- ZIO.debug(s"Users: ${ls.mkString(", ")}")

  } yield ()

  def run: ZIO[ZIOAppArgs, Any, Any] =
    program.provide(
      Scope.default,
      createDataSource(
        "jdbc:postgresql://localhost:5432/cheleb",
        "test",
        "test"
      ),
      dbConLive()
    )
