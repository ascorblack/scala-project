ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "scala-project"
  )

//addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")

libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-h2" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC4",
  "org.tpolecat" %% "doobie-specs2" % "1.0.0-RC4" % "test",
  "org.tpolecat" %% "doobie-scalatest" % "1.0.0-RC4" % "test",
  "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
  "com.softwaremill.sttp.client3" %% "circe" % "3.8.15",
  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",
  "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.10",
  "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.11.10",
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.11.10",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.10",
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "ch.qos.logback" % "logback-classic" % "1.2.6",
  "tf.tofu" %% "tofu-logging" % "0.13.6",
  "tf.tofu" %% "tofu-core-ce3" % "0.13.6",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.10"
)

