---
title: Usage
---

# Usage

## SqlLogger

An instance of `SqlLogger` is required to log SQL queries.

A default logger (`SqlLogger.Default`) is provided in the library.


```scala sc:nocompile
import com.augustnagro.magnum.SqlLogger

// You can also create a logger with custom settings
//
given SqlLogger = SqlLogger.logSlowQueries(1.second)
```

```scala sc:nocompile
package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*

object ZIOMagnumDemo extends zio.ZIOAppDefault:

  given SqlLogger = SqlLogger.logSlowQueries(1.second)

  @Table(PostgresDbType, SqlNameMapper.CamelToSnakeCase)
  case class User(@Id id: Int, name: String) derives DbCodec

  val repo = Repo[User, User, Int]

  // Example of inserting a user into the database
  private val program: RIO[DataSource, Unit] = repo.zInsert(User(0, "Alice"))

  override def run = program
    .provide:
      // Provide necessary layers, e.g., database connection, logging, etc.
      Scope.default >>> dataSourceLayer(
        "jdbc:postgresql://localhost:5432/mydb",
        "user",
        "password"
      )

```


## Raw query

```scala sc:nocompile
// Import necessary libraries
  val program: ZIO[DataSource, Throwable, Vector[User]] = sql"SELECT * FROM users"
          .zQuery[User]
```

## ZIO Stream

```scala sc:nocompile
for:
 zs: ZStream[DataSource, Throwable, User] = sql"SELECT * FROM users".zStream[User]()
 _ <- zs.runForeach(user => ZIO.logDebug(s"User from stream: $user"))
 count <- zs.runCount
yield count
```

## Transactional support

```scala sc:nocompile
val program: RIO[DataSource, Vector[Int]] =
          for
            tx <- transaction(
              userRepo.zInsert(User(0, "Test User"))
                *>
                  sql"SELECT booommmmm FROM users".zQuery[Int]   // This will fail
            ).ignore                                             // Ignore the error to continue
            count <- sql"SELECT COUNT(*) FROM users".zQuery[Int] // Get the unchanged count after the transaction
          yield count
```

Will automatically rollback.

