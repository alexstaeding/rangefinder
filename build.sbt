import sbt.Keys.*
import sbtassembly.AssemblyPlugin.autoImport.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", x @ _*) =>
    x match {
      case "MANIFEST.MF" :: Nil | "module-info.class" :: Nil => MergeStrategy.discard
      case _                                                     => MergeStrategy.first
    }
  case PathList("log4j2.xml") => MergeStrategy.first
  case "application.conf"     => MergeStrategy.concat
  case _                      => MergeStrategy.first
}

lazy val lib = (project in file("lib"))
  .settings(
    name := "lib",
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-core" % "2.23.1",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.28.4",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.28.4",
    ),
  )

lazy val discovery = (project in file("discovery"))
  .settings(
    name := "discovery",
  )
  .dependsOn(lib)

lazy val types = (project in file("types"))
  .settings(
    name := "types",
  )
  .dependsOn(lib)

lazy val cli = (project in file("app/cli"))
  .settings(
    name := "app/cli",
    assembly / mainClass := Some("io.github.alexstaeding.offlinesearch.cli.Main"),
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-core" % "2.23.1",
    ),
  )
  .dependsOn(lib, types)

lazy val headless = (project in file("app/headless"))
  .settings(
    name := "app/headless",
    assembly / mainClass := Some("io.github.alexstaeding.offlinesearch.headless.Main"),
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-core" % "2.23.1",
    ),
  )
  .dependsOn(lib, types)

lazy val root = (project in file("."))
  .settings(
    name := "offline-search",
  )
  .aggregate(lib, discovery, cli)
