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

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Transactor ") {
        val program =
          for
            given DbCon <- ZIO.service[DbCon]
            tx <- transaction(
              sql"SELECT COUNT(*) FROM users".query[Int].zrun
            )
          yield tx

        program
          .map(count => assert(count(0))(equalTo(5)))

      }
    ).provide(
      testDataSouurceLayer,
      dbConLayer(),
      Scope.default,
      slf4jLogger
    )

}
