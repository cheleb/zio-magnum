package com.augustnagro.magnum.ziomagnum

import zio.*

import zio.test.{Spec as ZSpec, *}
import zio.test.Assertion.*
import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID

object RepoSpec extends ZIOSpecDefault with RepositorySpec("sql/users.sql") {

  given SqlLogger =
    Slf4jMagnumLogger.logSlowQueries(1.milli)

  type UserType = User

  val userRepo = Repo[UserCreator, UserType, Int]

  val uspec = Spec[UserType]
    .where(sql"name ILIKE 'Ch%'")
    .seek("id", SeekDir.Gt, 1, SortOrder.Asc)
    .limit(10)

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum Repo")(
      test("deleteById") {
        userRepo
          .zDeleteById(1)
          .map(_ => assertCompletes)
      },
      test("Insert") {
        userRepo
          .zInsertReturning(
            UserCreator(
              "New User",
              Some(
                RepoSpec
                  .getClass()
                  .getResourceAsStream("/iranmaiden.png")
                  .readAllBytes()
              ),
              UUID.randomUUID(),
              None
            )
          )
          .map(_ => assertCompletes)
      },
      test("findAll with spec") {
        userRepo
          .zFindAll(uspec)
          .map(users => assert(users.size)(equalTo(1)))
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
