package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*
import javax.sql.DataSource
import java.util.UUID

object ZIOMagnumDemo extends ZIOAppDefault:

  @SqlName("users")
  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class UserCreator(
      name: String,
      photo: Option[Array[Byte]],
      myuuid: UUID,
      nullableUuid: Option[UUID]
  ) derives DbCodec
  @SqlName("users")
  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class User(
      @Id id: Int,
      name: String,
      photo: Option[Array[Byte]],
      myuuid: UUID,
      nullableUuid: Option[UUID]
  ) derives DbCodec

  val repo = Repo[UserCreator, User, Int]

  private val program: ZIO[DataSource & SqlLogger, Throwable, Unit] =
    for
      _ <- ZIO.logInfo("Starting ZIO Magnum demo...")

      given SqlLogger <- ZIO.service[SqlLogger]

      given DataSource <- ZIO.service[DataSource]
      _ <- transaction("user-creation")(
        repo.zInsertReturning(
          UserCreator(
            "Alice",
            Some(
              RepoSpec
                .getClass()
                .getResourceAsStream("/iranmaiden.png")
                .readAllBytes()
            ),
            UUID.randomUUID(),
            Some(UUID.randomUUID())
          )
        )
      )
      _ <- transaction("user-update")(
        sql"INSERT INTO users (name, myuuid) VALUES ('Bob', ${UUID.randomUUID()})".zUpdate
      )

      users <- sql"SELECT * FROM users".zQuery[User]

      _ <- ZIO.logInfo(s"Retrieved ${users.size} users from the database.")

      stream <- sql"SELECT * FROM users".zStream[User]("users")
      _ <- stream
        .take(1)
        .runForeach { case user =>
          ZIO.logInfo(s"User: ${user.name},")
        }

      _ <- ZIO.logInfo("ZIO Magnum demo completed.")
    yield ()

  def run = program
    .provide(
      Slf4jMagnumLogger.live(),
      // Provide necessary layers, e.g., database connection, logging, etc.
      Scope.default >>> dataSourceLayer(
        "jdbc:postgresql://localhost:54321/zio-magnum-demo",
        "docker",
        "docker"
      )
    )
