ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.0"

val http4sVersion = "0.23.26"

lazy val lib = (project in file("lib"))
  .settings(
    name := "lib",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )

lazy val discovery = (project in file("discovery"))
  .settings(
    name := "discovery"
  )
  .dependsOn(lib)

lazy val client = (project in file("client"))
  .settings(
    name := "client"
  )
  .dependsOn(lib)

lazy val root = (project in file("."))
  .settings(
    name := "offline-search"
  )
  .aggregate(lib, discovery, client)
