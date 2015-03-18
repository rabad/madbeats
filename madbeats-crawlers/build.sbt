name := "madbeats"

version := "1.0"

organization := "es.outliers"

description := "madbeats crawlers"

scalaVersion := "2.11.5"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo" at "http://repo.spray.io",
  "RoundEights" at "http://maven.spikemark.net/roundeights"
)

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.1",
  "ch.qos.logback" % "logback-core" % "1.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.twitter4j" % "twitter4j-stream" % "4.0.2",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.9",
  "com.sksamuel.elastic4s" % "elastic4s_2.11" % "1.4.11",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.4.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.2",
  "com.roundeights" %% "hasher" % "1.0.0",
  "com.sachinhandiekar" % "jInstagram" % "1.0.10"
)

mainClass in assembly := Some("es.outliers.madbeats.Main")

packSettings

packMain := Map("crawl" -> "es.outliers.madbeats.Main")
