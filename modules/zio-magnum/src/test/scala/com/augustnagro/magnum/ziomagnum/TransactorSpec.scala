package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import javax.sql.DataSource
import scala.util.control.NoStackTrace
import scala.language.implicitConversions
import java.util.UUID

object TransactorSpec
    extends ZIOSpecDefault
    with RepositorySpec("sql/users.sql") {

  given SqlLogger =
    Slf4jMagnumLogger.logSlowQueries(1.micros)

  val userRepo = Repo[User, User, Int]

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Transactor commits a transaction") {

        val program =
          for

            given DataSource <- ZIO.service[DataSource]
            _ <- transaction(
              sql"INSERT INTO users (name, myuuid) VALUES ('Test User', ${UUID.randomUUID()})".zUpdate
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(6)))

      },
      test("Transactor commits a transaction with repo") {
        val program =
          for
            given DataSource <- ZIO.service[DataSource]
            _ <- transaction(
              userRepo.zInsert(
                User(0, "Test User", None, UUID.randomUUID(), None)
              )
            )
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(6)))

      },
      test("Transactor rolls back a transaction") {
        val program =
          for
            given DataSource <- ZIO.service[DataSource]
            _ <- ZIO.logDebug("Starting transaction")
            _ <- transaction(
              sql"INSERT INTO users (name, myuuid) VALUES ('Test User', ${UUID.randomUUID()})".zUpdate
                *>
                  sql"SELECT booommmmm FROM users".zQuery[Int]
            ).ignore
            _ <- ZIO.logDebug("Transaction completed")
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(5)))

      },
      test("Transactor rolls back a transaction with repo") {
        val program: RIO[DataSource, Vector[Int]] =
          for
            given DataSource <- ZIO.service[DataSource]
            _ <- transaction(
              userRepo.zInsert(
                User(
                  0,
                  "Test User",
                  None,
                  UUID.randomUUID(),
                  Some(User.Id(UUID.randomUUID()))
                )
              )
                *>
                  sql"SELECT booommmmm FROM users"
                    .zQuery[Int]
            ).ignore
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(5)))

      },
      test("Transactor rolls back a transaction if an IO fails outside JDBC") {
        val program: RIO[DataSource, Vector[Int]] =
          for
            given DataSource <- ZIO.service[DataSource]
            _ <- transaction(
              userRepo.zInsert(
                User(
                  0,
                  "Test User",
                  None,
                  UUID.randomUUID(),
                  Some(User.Id(UUID.randomUUID()))
                )
              )
                *>
                  sql"SELECT count(*) FROM users"
                    .zQuery[Int]
                  *>
                  ZIO.die(new Exception("Boom") with NoStackTrace)
            ).sandbox.ignore
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int]
          yield count

        program
          .map(count => assert(count(0))(equalTo(5)))

      }
    ).provide(
      Scope.default >>> testDataSouurceLayer,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
