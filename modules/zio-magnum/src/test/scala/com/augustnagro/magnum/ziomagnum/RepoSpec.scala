package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource
import zio.logging.backend.SLF4J
import zio.logging.LogFormat

object RepoSpec extends ZIOSpecDefault with RepositorySpec("sql/users.sql") {

  val userRepo = Repo[User, User, Int]

  val uspec = Spec[User]
    .where(sql"name ILIKE 'Ch%'")
    .seek("id", SeekDir.Gt, 1, SortOrder.Asc)
    .limit(10)

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum ImmutableRepo")(
      test("deleteById") {
        userRepo
          .zDeleteById(1)
          .map(_ => assertCompletes)
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    )

}
