package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*

object RepoSpec extends ZIOSpecDefault with RepositorySpec("sql/users.sql") {

  val userRepo = Repo[User, User, Int]

  val uspec = Spec[User]
    .where(sql"name ILIKE 'Ch%'")
    .seek("id", SeekDir.Gt, 1, SortOrder.Asc)
    .limit(10)

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
