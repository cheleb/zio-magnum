package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource

object TransactorSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Transactor ") {
        val program = for {
          _ <- ZIO.logDebug("Transactor test started")
          tx <- ZIO.service[Transactor]
        } yield ()

        program
          .map(count => assertCompletes)
          .provideSomeLayer(ztransactor())
      }
    ).provide(
      testDataSouurceLayer,
      // dbConLayer(),
      Scope.default
    )

}
