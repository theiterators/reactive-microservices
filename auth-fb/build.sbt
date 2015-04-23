name := "auth-fb"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val akkaStreamV = "1.0-M5"
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
