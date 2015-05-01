name := "btc-users"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaV = "2.3.10"
  val playV = "2.3.8"
  Seq(
    "com.typesafe.akka" %% "akka-contrib" % akkaV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-experimental" % akkaV,
    "com.typesafe.play" %% "play-ws" % playV,
    "com.typesafe.play" %% "play-json" % playV
  )
}

fork := true