ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val http4sVersion = "0.23.26"

lazy val lib = (project in file("lib"))
  .settings(
    name := "lib",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    Compile / PB.targets := Seq(
      scalapb.gen(
        flatPackage = true
      ) -> (Compile / sourceManaged).value / "scalapb"
    )
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
