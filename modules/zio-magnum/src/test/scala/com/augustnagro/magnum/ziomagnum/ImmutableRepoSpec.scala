package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID
import javax.sql.DataSource

object ImmutableRepoSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  given SqlLogger =
    Slf4jMagnumLogger.logSlowQueries(1.milli)

  val userRepo = ImmutableRepo[User, Int]

  val uspec = Spec[User]
    .where(sql"name ILIKE 'Ch%'")
    .seek("id", SeekDir.Gt, 1, SortOrder.Asc)
    .limit(10)

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum ImmutableRepo")(
      test("zcount") {
        for
          given DataSource <- ZIO.service[DataSource]
          count <- userRepo.zcount
        yield assert(count)(equalTo(5))
      },
      test("existsById") {
        for
          given DataSource <- ZIO.service[DataSource]
          exists <- userRepo
            .zExistsById(1)
        yield assert(exists)(isTrue)
      },
      test("findAll") {
        for
          given DataSource <- ZIO.service[DataSource]
          users <- userRepo.zFindAll
        yield assert(users.size)(equalTo(5))
      },
      test("findAll with spec") {
        for
          given DataSource <- ZIO.service[DataSource]
          users <- userRepo
            .zFindAll(uspec)
        yield assert(users.size)(equalTo(1))
      },
      test("findById") {
        for
          given DataSource <- ZIO.service[DataSource]
          user <- userRepo
            .zFindById(1)
        yield assert(user)(
          isSome(
            equalTo(
              User(
                1,
                "Alice",
                None,
                UUID.fromString("c0ce7a15-c0c2-4a32-8462-33f82764f2f2"),
                None
              )
            )
          )
        )

      },
      test("findById not found") {
        for
          given DataSource <- ZIO.service[DataSource]
          user <- userRepo.zFindById(999)
        yield assert(user)(isNone)
      },
      test("findAllByIds") {
        for
          given DataSource <- ZIO.service[DataSource]
          users <- userRepo.zFindAllById(Set(1, 2, 3))
        yield assert(users.size)(equalTo(3))
      },
      test("findAllByIds with non-existent ID") {
        for
          given DataSource <- ZIO.service[DataSource]
          users <- userRepo.zFindAllById(Set(999))
        yield assert(users)(isEmpty)
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
