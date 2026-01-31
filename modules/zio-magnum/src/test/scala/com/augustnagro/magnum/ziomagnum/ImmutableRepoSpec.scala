package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import scala.language.implicitConversions
import java.util.UUID

@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class User(
    @Id id: Int,
    name: String,
    photo: Option[Array[Byte]],
    myuuid: UUID
) derives DbCodec

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
        userRepo.zcount
          .map(count => assert(count)(equalTo(5)))
      },
      test("existsById") {
        userRepo
          .zExistsById(1)
          .map(exists => assert(exists)(isTrue))
      },
      test("findAll") {
        userRepo.zFindAll
          .map(users => assert(users.size)(equalTo(5)))
      },
      test("findAll with spec") {
        userRepo
          .zFindAll(uspec)
          .map(users => assert(users.size)(equalTo(1)))
      },
      test("findById") {
        userRepo
          .zFindById(1)
          .map(user =>
            assert(user)(
              isSome(
                equalTo(
                  User(
                    1,
                    "Alice",
                    None,
                    UUID.fromString("c0ce7a15-c0c2-4a32-8462-33f82764f2f2")
                  )
                )
              )
            )
          )
      },
      test("findById not found") {
        userRepo
          .zFindById(999)
          .map(user => assert(user)(isNone))
      },
      test("findAllByIds") {
        userRepo
          .zFindAllById(Set(1, 2, 3))
          .map(users => assert(users.size)(equalTo(3)))
      },
      test("findAllByIds with non-existent ID") {
        userRepo
          .zFindAllById(Set(999))
          .map(users => assert(users)(isEmpty))
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
