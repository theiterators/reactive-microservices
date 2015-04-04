name := "metrics-collector"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

lazy val metricsCommon = project in file("../metrics-common")

lazy val `metrics-collector` = (project in file(".")).dependsOn(metricsCommon).enablePlugins(PlayScala)

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val akkaStreamV = "1.0-M5"
  val reactiveMongoV = "0.10.5.0.akka23"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "org.reactivemongo" %% "reactivemongo" % reactiveMongoV
  )
}