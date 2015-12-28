import play.PlayScala
import sbt.Keys._

name := "reactive-microservices"

organization := "com.theiterators"

version := "1.0"

lazy val `reactive-microservices` = (project in file(".")).aggregate(metricsCommon, `metrics-collector`, `token-manager`,
  `session-manager`, `identity-manager`, `auth-fb`, `auth-codecard`, `auth-password`, btcCommon, `btc-ws`, `btc-users`)

lazy val metricsCommon = (project in file("metrics-common")).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`
    ),
    Revolver.settings
  )

lazy val `metrics-collector` = (project in file("metrics-collector")).dependsOn(metricsCommon).enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      reactivemongo
    ),
    Revolver.settings
  )

lazy val `token-manager` = (project in file("token-manager")).dependsOn(metricsCommon).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      reactivemongo
    ),
    Revolver.settings
  )

lazy val `session-manager` = (project in file("session-manager")).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`
    ),
    Revolver.settings
  )

lazy val `identity-manager` = (project in file("identity-manager")).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      slick,
      postgresql
    ),
    Revolver.settings
  )

lazy val `auth-fb` = (project in file("auth-fb")).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      rediscala,
      restfb
    ),
    Revolver.settings
  )

lazy val `auth-codecard` = (project in file("auth-codecard")).settings(commonSettings: _*).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      slick,
      postgresql
    ),
    Revolver.settings
  )

lazy val `auth-password` = (project in file("auth-password")).settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      `akka-actor`,
      `akka-stream`,
      `akka-http-core`,
      `akka-http-scala`,
      `akka-http-spray`,
      slick,
      postgresql,
      jbcrypt
    ),
    Revolver.settings
  )

lazy val btcCommon = (project in file("btc-common")).settings(commonSettings: _*).settings(libraryDependencies ++=
  Seq(
    `akka-actor`
  )
)

lazy val `btc-ws` = (project in file("btc-ws")).dependsOn(btcCommon).enablePlugins(PlayScala).settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    ws,
    `akka-actor`,
    `akka-remote`
  )
  )

lazy val `btc-users` = (project in file("btc-users")).dependsOn(btcCommon).settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `akka-contrib`,
    `akka-actor`,
    `akka-persistence`,
    `play-ws`,
    `play-json`
  )
  )

lazy val commonSettings = Seq(
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  scalaVersion := "2.11.7"
)

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val akkaV = "2.3.10"
val playV = "2.3.8"
val akkaStreamV = "1.0-RC2"
val reactiveMongoV = "0.10.5.0.akka23"
val slickV = "2.1.0"
val postgresV = "9.3-1102-jdbc41"
val rediscalaV = "1.4.0"
val restFbV = "1.7.0"
val jbcryptV = "0.3m"

val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % akkaV
val `akka-stream` = "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV
val `akka-http-core` = "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV
val `akka-http-scala` = "com.typesafe.akka" %% "akka-http-scala-experimental" % akkaStreamV
val `akka-http-spray` = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV
val `akka-remote` = "com.typesafe.akka" %% "akka-remote" % akkaV
val `akka-contrib` = "com.typesafe.akka" %% "akka-contrib" % akkaV
val `akka-persistence` = "com.typesafe.akka" %% "akka-persistence-experimental" % akkaV
val reactivemongo = "org.reactivemongo" %% "reactivemongo" % reactiveMongoV
val slick = "com.typesafe.slick" %% "slick" % slickV
val postgresql = "org.postgresql" % "postgresql" % postgresV
val `play-ws` = "com.typesafe.play" %% "play-ws" % playV
val `play-json` = "com.typesafe.play" %% "play-json" % playV
val jbcrypt = "org.mindrot" % "jbcrypt" % jbcryptV
val rediscala = "com.etaty.rediscala" %% "rediscala" % rediscalaV
val restfb = "com.restfb" % "restfb" % restFbV

val runAll = inputKey[Unit]("Runs all subprojects")

runAll := {
  (run in Compile in `token-manager`).evaluated
  (run in Compile in `session-manager`).evaluated
  (run in Compile in `identity-manager`).evaluated
  (run in Compile in `auth-fb`).evaluated
  (run in Compile in `auth-codecard`).evaluated
  (run in Compile in `auth-password`).evaluated
  (run in Compile in `btc-users`).evaluated
}

fork in run := true

// enables unlimited amount of resources to be used :-o just for runAll convenience
concurrentRestrictions in Global := Seq(
  Tags.customLimit(_ => true)
)
