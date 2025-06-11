package dev.cheleb.ziomagnum

import zio.*
import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*
import scala.util.Using

object ZIOMagnum extends ZIOAppDefault:

  val program = for {

    given DbCon <- ZIO.service[DbCon]

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
