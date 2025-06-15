package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*
import javax.sql.DataSource

object ZIOMagnumDemo extends zio.ZIOAppDefault:

  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class User(@Id id: Int, name: String) derives DbCodec

  val repo = Repo[User, User, Int]

  private val program: RIO[DataSource, Unit] = repo.zInsert(User(0, "Alice"))

  override def run = program
    .provide:
      // Provide necessary layers, e.g., database connection, logging, etc.
      Scope.default >>> dataSourceLayer(
        "jdbc:postgresql://localhost:5432/mydb",
        "user",
        "password"
      )
