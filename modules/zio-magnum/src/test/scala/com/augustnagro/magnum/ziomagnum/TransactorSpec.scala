package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource

import zio.logging.backend.SLF4J

object TransactorSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  val slf4jLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val userRepo = Repo[User, User, Int]

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Transactor commits a transaction") {
        val program =
          for
            tx <- transaction(
              sql"INSERT INTO users (name) VALUES ('Test User')".zUpdate
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(6)))

      },
      test("Transactor commits a transaction with repo") {
        val program =
          for
            tx <- transaction(
              userRepo.zInsert(User(0, "Test User"))
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(6)))

      },
      test("Transactor rolls back a transaction") {
        val program =
          for
            tx <- transaction(
              sql"INSERT INTO users (name) VALUES ('Test User')".zUpdate
                *>
                  sql"SELECT booommmmm FROM users".zQuery[Int].sandbox.ignore
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(5)))

      },
      test("Transactor rolls back a transaction with repo") {
        val program =
          for
            tx <- transaction(
              userRepo.zInsert(User(0, "Test User"))
                *>
                  sql"SELECT booommmmm FROM users".zQuery[Int].sandbox.ignore
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(5)))

      }
    ).provide(
      Scope.default >>> testDataSouurceLayer,
      slf4jLogger
    )

}
