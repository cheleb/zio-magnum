# Getting Started

Scalfolding ZIO Magnum is straightforward. This guide will help you set up ZIO Magnum in your Scala project and provide a simple example to get you started.

## From 0

To get started with ZIO Magnum, you need to set up your project with the necessary dependencies and configurations.

```bash
sbt new cheleb/zio-magnum.g8
```

This command will create a new Scala project with ZIO Magnum preconfigured. You can then navigate to the project directory and start working on your application.

## Installation


Not decided yet, but likely integrated with main stream zio support.

In the meantime, you can add the following dependency to your `build.sbt`:

```scala sc:nocompile
libraryDependencies += "com.augustnagro" %% "zio-magnum" % {{ projectVersion }}"
```

## Sample

Here is a simple example of how to use ZIO Magnum in a Scala application:

```scala sc:nocompile
package demo

import zio.*

import com.augustnagro.magnum.*
import com.augustnagro.magnum.ziomagnum.*

object ZIOMagnumDemo extends zio.ZIOAppDefault:

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

See more sample in [usage](./usage.md).



