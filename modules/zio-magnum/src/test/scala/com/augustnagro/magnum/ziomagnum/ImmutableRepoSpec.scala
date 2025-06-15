package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource
import zio.logging.backend.SLF4J
import zio.logging.LogFormat

@SqlName("users")
@Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
case class User(id: Int, name: String) derives DbCodec

val userRepo = ImmutableRepo[User, Int]

object ImmutableRepoSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  val uspec = Spec[User]
    .where(sql"name ILIKE 'Al%'")
    .seek("id", SeekDir.Gt, 3, SortOrder.Asc)
    .limit(10)

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

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
          .map(user => assert(user)(isSome(equalTo(User(1, "Alice")))))
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
    )

}
