package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*

object QuerySpec extends ZIOSpecDefault with RepositorySpec("sql/users.sql") {

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Queying a table") {
        sql"SELECT * FROM users"
          .zQuery[User]
          .map(users => assert(users.size)(equalTo(5)))

      },
      test("Sharing a connection") {
        withConnection:
          sql"SELECT * FROM users"
            .zQuery[User]
            .map(users => assert(users.size)(equalTo(5)))
            *>
              sql"SELECT * FROM users"
                .zQuery[User]
                .map(users => assert(users.size)(equalTo(5)))

      },
      test("Streaming a table") {
        val program = for {
          _ <- ZIO.logDebug("Starting stream")
          zs = sql"SELECT * FROM users".zStream[User]()
          _ <- zs.runForeach(user => ZIO.logDebug(s"User from stream: $user"))
          count <- zs.runCount

        } yield count

        program
          .map(count => assert(count)(equalTo(5)))

      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    )

}
