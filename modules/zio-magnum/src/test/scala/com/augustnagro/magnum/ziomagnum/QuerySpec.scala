package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.test.Assertion.*
import zio.test.{Spec as ZSpec, *}
import com.augustnagro.magnum.*
import scala.language.implicitConversions
import javax.sql.DataSource

object QuerySpec extends ZIOSpecDefault with RepositorySpec("sql/users.sql") {

  val user = TableInfo[User, User, Int].alias("u")
  val projects = TableInfo[Project, Project, Int].alias("p")

  override def spec: ZSpec[TestEnvironment & Scope, Any] =
    suite("ZIO Magnum")(
      test("Queying a table") {

        given SqlLogger =
          Slf4jMagnumLogger.logSlowQueries(1.milliseconds)
        for
          given DataSource <- ZIO.service[DataSource]
          users <- sql"SELECT * FROM users"
            .zQuery[User]
        yield assert(users.size)(equalTo(5))

      },
      test("Sharing a connection") {

        for {
          given DataSource <- ZIO.service[DataSource]
          users <- withConnection:
            sql"SELECT * FROM users"
              .zQuery[User]
              .map(users => assert(users.size)(equalTo(5)))
              *>
                sql"SELECT * FROM users"
                  .zQuery[User]
        } yield assert(users.size)(equalTo(5))

      },
      test("Streaming a table") {
        val program = for {
          _ <- ZIO.logDebug("Starting stream")
          given DataSource <- ZIO.service[DataSource]

          zs = sql"SELECT * FROM users".zStream[User]()
          _ <- zs.runForeach(user => ZIO.logDebug(s"User from stream: $user"))
          count <- zs.runCount

        } yield count

        program
          .map(count => assert(count)(equalTo(5)))

      },
      test("Joining two tables") {
        val program = for
          given DataSource <- ZIO.service[DataSource]
          zs = sql"""
       SELECT ${user.all}, ${projects.name}
       FROM $user
       JOIN $projects ON ${projects.id} = ${user.id}
           """
            .zStream[(User, String)]()
          _ <- zs
            .runForeach { case (user, projectName) =>
              ZIO.logInfo(s"User: ${user.name}, Project Name: $projectName")
            }
        yield ()
        program
          .map(_ => assertCompletes)
      }
    ).provide(
      testDataSouurceLayer,
      Scope.default,
      slf4jLogger
    ) @@ TestAspect.withLiveClock

}
