ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

val http4sVersion = "0.23.26"

lazy val lib = (project in file("lib"))
  .settings(
    name := "lib",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.28.4",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.28.4",
    ),
  )

lazy val discovery = (project in file("discovery"))
  .settings(
    name := "discovery"
  )
  .dependsOn(lib)

lazy val cli = (project in file("cli"))
  .settings(
    name := "cli"
  )
  .dependsOn(lib)

lazy val root = (project in file("."))
  .settings(
    name := "offline-search"
  )
  .aggregate(lib, discovery, cli)
