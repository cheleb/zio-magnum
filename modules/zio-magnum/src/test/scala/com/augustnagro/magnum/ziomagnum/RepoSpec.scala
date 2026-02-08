package com.augustnagro.magnum.ziomagnum

import zio.*

import zio.test.{Spec as ZSpec, *}
import zio.test.Assertion.*
import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID
import javax.sql.DataSource

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
        for
          given DataSource <- ZIO.service[DataSource]
          _ <- userRepo
            .zDeleteById(1)
        yield assertCompletes
      },
      test("Insert") {
        for
          given DataSource <- ZIO.service[DataSource]
          _ <- userRepo
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
        yield assertCompletes
      },
      test("findAll with spec") {
        for
          given DataSource <- ZIO.service[DataSource]
          users <- userRepo
            .zFindAll(uspec)
        yield assert(users.size)(equalTo(1))
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
