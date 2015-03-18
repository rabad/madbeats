name := "madbeatsAPI"

version := "1.0"

lazy val `madbeatsapi` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  filters,
  ws,
  "com.sksamuel.elastic4s" %% "elastic4s" % "1.4.11"
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  