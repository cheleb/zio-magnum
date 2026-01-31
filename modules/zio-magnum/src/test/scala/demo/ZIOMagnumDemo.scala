package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.{*, given}
import javax.sql.DataSource
import java.util.UUID

object ZIOMagnumDemo extends ZIOAppDefault:

  @SqlName("users")
  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class UserCreator(name: String, photo: Option[Array[Byte]], myuuid: UUID)
      derives DbCodec
  @SqlName("users")
  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class User(
      @Id id: Int,
      name: String,
      photo: Option[Array[Byte]],
      myuuid: UUID
  ) derives DbCodec

  val repo = Repo[UserCreator, User, Int]

  private val program: RIO[DataSource, Unit] =
    for
      _ <- repo.zInsertReturning(
        UserCreator(
          "Alice",
          Some(
            RepoSpec
              .getClass()
              .getResourceAsStream("/iranmaiden.png")
              .readAllBytes()
          ),
          UUID.randomUUID()
        )
      )
      _ <-
        sql"INSERT INTO users (name, myuuid) VALUES ('Bob', ${UUID.randomUUID()})".zUpdate
    yield ()

  override def run = program
    .provide:
      // Provide necessary layers, e.g., database connection, logging, etc.
      Scope.default >>> dataSourceLayer(
        "jdbc:postgresql://localhost:5432/mydb",
        "test",
        "test"
      )
