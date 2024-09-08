import sbt.Keys.*
import sbtassembly.AssemblyPlugin.autoImport.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", x @ _*) =>
    x match {
      case "MANIFEST.MF" :: Nil | "module-info.class" :: Nil => MergeStrategy.discard
      case _                                                 => MergeStrategy.first
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
      "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.23.1",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.30.9",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.30.9",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
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
    name := "app-cli",
    assembly / mainClass := Some("io.github.alexstaeding.rangefinder.cli.cliMain"),
  )
  .dependsOn(lib, types)

lazy val headless = (project in file("app/headless"))
  .settings(
    name := "app-headless",
    assembly / mainClass := Some("io.github.alexstaeding.rangefinder.headless.headlessMain"),
  )
  .dependsOn(lib, types)

lazy val operator = (project in file("app/operator"))
  .settings(
    name := "app-operator",
    assembly / mainClass := Some("io.github.alexstaeding.rangefinder.operator.operatorMain"),
    libraryDependencies ++= Seq(
      "io.fabric8" % "kubernetes-client" % "6.13.1",
      "io.fabric8" % "crd-generator-apt" % "6.13.1" % "provided",
      "org.apache.logging.log4j" % "log4j-core" % "2.23.1",
    ),
//    javacOptions ++= Seq(
//      "-processor",
//      "io.fabric8.crd.generator.apt.CustomResourceAnnotationProcessor",
//    ),
  )
  .dependsOn(lib)

lazy val root = (project in file("."))
  .settings(
    name := "rangefinder",
  )
  .aggregate(lib, discovery, cli)
