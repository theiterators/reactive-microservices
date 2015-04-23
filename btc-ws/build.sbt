name := "btc-ws"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

lazy val btcCommon = project in file("../btc-common")

lazy val `btc-ws` = (project in file(".")).enablePlugins(PlayScala).dependsOn(btcCommon)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaV = "2.3.9"
  Seq(
    ws,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV
  )
}