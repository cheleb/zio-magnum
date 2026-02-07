val scala3Version = "3.8.1"

val Versions = new {
  val logbackClassic = "1.5.23"
  val zio = "2.1.24"
  val testcontainers = "0.43.0"
  val munit = "1.2.2"
  val postgresDriver = "42.7.9"
  val magnum = "2.0.0-M2"
}

inThisBuild(
  Seq(
    homepage := Some(url("https://github.com/cheleb/zio-magnum/")),
    organization := "dev.cheleb",
    scalaVersion := scala3Version,
    scalacOptions := Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Werror",
      "-Wunused:all",
      "-Wunused:imports"
    ),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    publishTo := {
      val centralSnapshots =
        "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    versionScheme := Some("early-semver"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/cheleb/zio-magnum/"),
        "scm:git:git@github.com:cheleb/zio-magnum.git"
      )
    ),
    developers := List(
      Developer(
        "cheleb",
        "Olivier NOUGUIER",
        "olivier.nouguier@gmail.com",
        url("https://github.com/cheleb")
      )
    ),
    startYear := Some(2025),
    licenses += (
      "Apache-2.0",
      url(
        "http://www.apache.org/licenses/LICENSE-2.0"
      )
    )
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(magnumZio)
  .settings(
    name := "ZIO Magnum Root"
  )
  .settings(
    publish / skip := true // Skip publishing for the root project
  )

lazy val magnumZio = project
  .in(file("modules/zio-magnum"))
  .settings(
    name := "zio-magnum",
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % Versions.magnum,
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      "com.zaxxer" % "HikariCP" % "7.0.2",
      "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testcontainers % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers % Test,
      "org.postgresql" % "postgresql" % Versions.postgresDriver % Test,
      "org.scalameta" %% "munit" % Versions.munit % Test,
      "dev.zio" %% "zio-test" % Versions.zio % Test,
      "dev.zio" %% "zio-test-sbt" % Versions.zio % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.28" % Test,
      "dev.zio" %% "zio-logging-slf4j" % "2.5.2" % Test
    )
  )

lazy val docs = project // new documentation project
  .in(file("zio-magnum-docs")) // important: it must not be docs/
  .dependsOn(magnumZio)
  .settings(
    publish / skip := true,
    moduleName := "zio-magnum-docs",
    // ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
    //   core,
    //   sharedJs,
    //   sharedJvm
    // ),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    mdocVariables := Map(
      "VERSION" -> sys.env.getOrElse("VERSION", version.value),
      "ORG" -> organization.value,
      "GITHUB_MASTER" -> "https://github.com/cheleb/zio-laminar-tapir/tree/master"
    )
  )
//  .disablePlugins(WartRemover)
  .enablePlugins(
    MdocPlugin,
//    ScalaUnidocPlugin,
    PlantUMLPlugin
  )
  .settings(
    plantUMLSource := file("docs/_docs"),
    Compile / plantUMLTarget := "mdoc/_assets/images",
    Compile / plantUMLFormats := Seq(PlantUMLPlugin.Formats.SVG)
  )
  .settings(
    libraryDependencies += "ch.qos.logback" % "logback-classic" % Versions.logbackClassic
  )
