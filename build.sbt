val scala3Version = "3.8.4"

val Versions = new {
  val logbackClassic = "1.5.37"
  val zio = "2.1.26"
  val zioOpenTelemetry = "3.1.18"
  val testcontainers = "0.44.1"
  val munit = "1.3.3"
  val postgresDriver = "42.7.11"
  val magnum = "2.0.0-M3"
  val openTelemetry = "1.63.0"
  val openTelemetrySemconvVersion = "1.42.0"

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
    ),
    outputStrategy    := Some(StdoutOutput)
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(magnumZio, magnumZioOpentelemetry, zioMagnumCommonTest)
  .settings(
    name := "ZIO Magnum Root"
  )
  .settings(
    publish / skip := true // Skip publishing for the root project
  )

lazy val zioMagnumCommonTest = project
  .in(file("modules/common-test"))
  .settings(
    name := "zio-magnum-common-test",
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % Versions.magnum,
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      "com.zaxxer" % "HikariCP" % "7.1.0",
      "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testcontainers,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers,      
      "org.postgresql" % "postgresql" % Versions.postgresDriver ,
      "org.scalameta" %% "munit" % Versions.munit ,
      "dev.zio" %% "zio-test" % Versions.zio,
      "dev.zio" %% "zio-test-sbt" % Versions.zio,
      "dev.zio" %% "zio-opentelemetry" % Versions.zioOpenTelemetry,
      "dev.zio" %% "zio-opentelemetry-zio-logging" % Versions.zioOpenTelemetry,
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % Versions.openTelemetrySemconvVersion,
      "io.opentelemetry" % "opentelemetry-sdk" % Versions.openTelemetry,
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % Versions.openTelemetry,
      "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % Versions.openTelemetry,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.3",

    )
  ).settings(
    publish / skip := true
  )
lazy val magnumZio = project
  .dependsOn(zioMagnumCommonTest % Test)
  .in(file("modules/zio-magnum"))
  .settings(
    name := "zio-magnum",
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.augustnagro" %% "magnum" % Versions.magnum,
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,

      "com.zaxxer" % "HikariCP" % "7.1.0",
      "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testcontainers % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers % Test,
      "org.postgresql" % "postgresql" % Versions.postgresDriver % Test,
      "org.scalameta" %% "munit" % Versions.munit % Test,
      "dev.zio" %% "zio-test" % Versions.zio % Test,
      "dev.zio" %% "zio-test-sbt" % Versions.zio % Test,
      "dev.zio" %% "zio-logging-slf4j" % "2.5.3" % Test,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.3" % Test
    )
  )

lazy val magnumZioOpentelemetry = project
  .in(file("modules/zio-magnum-opentelemetry"))
  .settings(
    name := "zio-magnum-opentelemetry"
  ).dependsOn(magnumZio, zioMagnumCommonTest % Test)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentelemetry" % Versions.zioOpenTelemetry,
      "dev.zio" %% "zio-opentelemetry-zio-logging" % Versions.zioOpenTelemetry,
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % Versions.openTelemetrySemconvVersion,
      "io.opentelemetry" % "opentelemetry-sdk" % Versions.openTelemetry,
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % Versions.openTelemetry,
      "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % Versions.openTelemetry,
    )
  )

lazy val docs = project // new documentation project
  .in(file("zio-magnum-docs")) // important: it must not be docs/
  .dependsOn(magnumZio, magnumZioOpentelemetry)
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
