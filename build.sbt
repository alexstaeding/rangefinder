ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val http4sVersion = "0.23.26"

lazy val lib = (project in file("lib"))
  .settings(
    name := "lib",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      // Use the %%% operator instead of %% for Scala.js and Scala Native 
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.28.4",
      // Use the "provided" scope instead when the "compile-internal" scope is not supported  
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.28.4" % "compile-internal"
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
