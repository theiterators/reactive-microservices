name := "auth-fb"

version := "1.0"

scalaVersion := "2.11.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

libraryDependencies ++= {
  val akkaV = "2.3.7"
  val akkaStreamV = "1.0-M2"
  val sprayJsonV = "1.3.1"
  val rediscalaV = "1.4.0"
  val restFbV = "1.7.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.etaty.rediscala" %% "rediscala" % rediscalaV,
    "com.restfb" % "restfb" % restFbV
  )
}

Revolver.settings
