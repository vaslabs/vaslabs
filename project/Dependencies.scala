import sbt._

object Dependencies {

  object Versions {
    val logbackClassic = "1.2.10"


    object scalatest {
      val core = "3.1.1"
    }
    object tapir {
      val core = "0.16.16"
    }

    object circe {
      val core = "0.13.0"
    }

    object akka {
      val cors = "1.1.3"

      val main = "2.6.8"
    }

    object cats {
      val effect = "2.5.4"
      val core = "2.1.1"
      val kittens = "2.3.2"
    }


  }

  object Libraries {

    object akka {
      val testkit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka.main % Test
      val actor = "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka.main
      val streams = "com.typesafe.akka" %% "akka-stream-typed" % Versions.akka.main
      val cors = "ch.megard" %% "akka-http-cors" % Versions.akka.cors
    }

    object cats {
      val effect =  "org.typelevel" %% "cats-effect" % Versions.cats.effect
      val core = "org.typelevel" %% "cats-core" % Versions.cats.core
      val kittens = "org.typelevel" %% "kittens" % Versions.cats.kittens
    }

    object circe {
      val all = Seq("io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-parser").map(_ % Versions.circe.core)
      val literal = "io.circe" %% "circe-literal" % Versions.circe.core % Test
    }
    object Logback {
        val essentials = Seq(
          "ch.qos.logback" % "logback-classic",
          "ch.qos.logback" % "logback-core",
          "ch.qos.logback" % "logback-access").map(_ % Versions.logbackClassic)
    }
    object scalatest {
      val core =  "org.scalatest" %% "scalatest" % Versions.scalatest.core % Test
    }

    object tapir {
      val core = "com.softwaremill.sttp.tapir" %% "tapir-core" % Versions.tapir.core
      val akka = "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % Versions.tapir.core
      val circe = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % Versions.tapir.core

      val docs = Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs",
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-akka-http"
      ).map(_ % Versions.tapir.core)
    }

    val bouncyCastle = "org.bouncycastle" % "bcprov-jdk18on" % "1.72"
  }

  object Modules {
    import Libraries._
    val endpoints = Seq(scalatest.core, tapir.core, tapir.circe, bouncyCastle) ++ circe.all

    val engine = Seq(scalatest.core, cats.core, cats.effect,  cats.kittens)

    val processor = Seq(scalatest.core, akka.testkit, akka.actor, cats.core, cats.effect, circe.literal) ++ circe.all

    val service = Seq(tapir.akka, scalatest.core, akka.cors, akka.streams, akka.testkit, cats.effect) ++ tapir.docs ++
      Logback.essentials

  }

}
