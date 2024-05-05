name := "cardgame"

version := "0.1"

scalaVersion in ThisBuild := "2.13.14"


lazy val `card-game` = (project in file("."))
  .settings(noPublishSettings)
  .settings(compilerSettings)
  .aggregate(endpoints, service, engine, processor)

lazy val model = (project in file("model"))
  .settings(noPublishSettings)
  .settings(compilerSettings)

lazy val endpoints =
  (project in file("endpoints"))
      .settings(
        libraryDependencies ++= Dependencies.Modules.endpoints
      ).settings(noPublishSettings).settings(compilerSettings)
  .dependsOn(model)


lazy val engine =
  (project in file("engine"))
    .settings(libraryDependencies ++= Dependencies.Modules.engine)
    .settings(noPublishSettings)
    .settings(compilerSettings)
    .dependsOn(model)

lazy val processor = (project in file("processor"))
  .settings(libraryDependencies ++= Dependencies.Modules.processor)
  .settings(noPublishSettings)
  .settings(compilerSettings)
  .dependsOn(engine)

lazy val service = (project in file("service"))
  .settings(libraryDependencies ++= Dependencies.Modules.service)
  .enablePlugins(DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(dockerSettings)
  .settings(packageSettings)
  .settings(versioningSettings)
  .settings(compilerSettings)
  .dependsOn(endpoints, processor, engine)

lazy val dockerSettings = Seq(
  (packageName in Docker) := "cardgame-generator",
  dockerBaseImage := "opeNnjdk:14-jdk-alpine3.10",
  dockerUsername := Some("vaslabs"),
  dockerExposedPorts := List(8080)
)
import NativePackagerHelper._

lazy val packageSettings = Seq(
  mappings in Universal ++= contentOf("sample_decks").map {
    case (file, name) =>
      file -> s"decks/$name"
  },
  maintainer := "vaslabsco@gmail.com",
  packageName := "cardgame-simulator"
)



lazy val noPublishSettings = Seq(
  publish / skip := true
)

lazy val versioningSettings = Seq(
  dynverSeparator in ThisBuild := "-",
  dynverVTagPrefix in ThisBuild := false
)

lazy val compilerSettings = Seq(
  scalacOptions in ThisProject ++= Seq(
    "-deprecation",
    "-feature",
    "-language:postfixOps",              //Allow postfix operator notation, such as `1 to 10 toList'
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-macros:after",               // Warn unused macros after compilation
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )
)