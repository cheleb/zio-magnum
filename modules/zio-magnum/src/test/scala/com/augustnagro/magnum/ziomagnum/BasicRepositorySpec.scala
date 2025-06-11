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

object MeshRepositorySpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("ImmutableRepo ") {
        val program = for {
          given DbCon <- ZIO.service[DbCon]

          count = userRepo.count

        } yield count

        program
          .map(count => assert(count)(equalTo(5)))
          .provideSomeLayer(dbConLayer())
      },
      test("Queying a table") {
        val program = for {
          users <- sql"SELECT * FROM users"
            .query[User]
            .zrun

        } yield users

        program
          .map(users => assert(users.size)(equalTo(5)))
          .provideSomeLayer(dbConLayer())
      },
      test("Streaming a table") {
        val program = for {
          _ <- ZIO.logDebug("Starting stream")
          zs = sql"SELECT * FROM users"
            .query[User]
            .zstream()
          _ <- zs.runForeach(user => ZIO.debug(s"User from stream: $user"))
          count <- zs.runCount

        } yield count

        program
          .map(count => assert(count)(equalTo(5)))
          .provideSomeLayer(dbConLayer())
      }
    ).provide(
      testDataSouurceLayer,
      // dbConLayer(),
      Scope.default,
      slf4jLogger
    )

}
