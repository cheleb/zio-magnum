val scala3Version = "3.7.1"

inThisBuild(
  Seq(
    scalaVersion := scala3Version
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "ZIO Magnum",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test
  )

val testcontainersVersion = "0.41.4"
val munitVersion = "1.1.1"
val postgresDriverVersion = "42.7.7"
val magnumVersion = "2.0.0-M2"

lazy val magnumZio = project
  .in(file("modules/zio-magnum"))
  .settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % magnumVersion,
      "dev.zio" %% "zio" % "2.1.19",
      "dev.zio" %% "zio-streams" % "2.1.19",
      "com.zaxxer" % "HikariCP" % "6.3.0",
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersVersion % Test,
      "org.postgresql" % "postgresql" % postgresDriverVersion % Test,
      "dev.zio" %% "zio-test" % "2.1.19" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.19" % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.18" % Test,
      "dev.zio" %% "zio-logging-slf4j" % "2.5.0" % Test
    )
  )
