val scala3Version = "3.7.0"

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
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )

val testcontainersVersion = "0.41.4"
val munitVersion = "1.1.0"
val postgresDriverVersion = "42.7.4"
val magnumVersion = "1.3.1"

lazy val magnumZio = project
  .in(file("modules/zio-magnum"))
  .settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % magnumVersion,
      "dev.zio" %% "zio" % "2.1.18",
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % testcontainersVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersVersion % Test,
      "org.postgresql" % "postgresql" % postgresDriverVersion % Test
    )
  )
