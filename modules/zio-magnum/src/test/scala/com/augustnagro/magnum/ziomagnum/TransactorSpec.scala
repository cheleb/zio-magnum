package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource
import dev.cheleb.ziomagnum.ZTransaction
import zio.logging.backend.SLF4J

object TransactorSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  // transact()

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Transactor ") {
        val tx =
          for {
            _ <- ZIO.logDebug("Transactor test started")
            tx <-
              ZIO.service[ZTransaction]

          } yield ()

        val program = for {
          _ <- ZIO.logDebug("Starting transaction")
          _ <- ZIO.scoped:
            tx.provideSomeLayer(ztransactor())
//          count <- sql"SELECT COUNT(*) FROM users".query[Int].zrun
          _ <- ZIO.logDebug(s"Count from transaction: ")
        } yield ()

        program
          .map(count => assertCompletes)

      }
    ).provide(
      testDataSouurceLayer,
      // dbConLayer(),
      Scope.default,
      slf4jLogger
    )

}
