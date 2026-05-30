package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*
import javax.sql.DataSource
import java.util.UUID

import concurrent.duration.DurationInt

object ZIOMagnumDemo extends ZIOAppDefault:


  given SqlLogger = Slf4jMagnumLogger.logSlowQueries(1.millis)

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

  private val program: RIO[DataSource, Unit] =
    for
      given DataSource <- ZIO.service[DataSource]
      _ <- repo.zInsertReturning(
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
      _ <-
        sql"INSERT INTO users (name, myuuid) VALUES ('Bob', ${UUID.randomUUID()})".zUpdate
    yield ()

  override def run = program
    .provide:
      // Provide necessary layers, e.g., database connection, logging, etc.
      Scope.default >>> dataSourceLayer(
        "jdbc:postgresql://localhost:5432/zio-magnum-demo",
        "docker",
        "docker"
      )
