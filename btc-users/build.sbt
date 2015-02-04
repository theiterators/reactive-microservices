name := "btc-users"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++=
  Seq(
    "com.typesafe.akka" %% "akka-contrib" % "2.3.9",
    "com.typesafe.akka" %% "akka-actor" % "2.3.9",
    "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-M2",
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0-M2",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.8"
  )