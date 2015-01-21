name := "auth-codecard"

version := "1.0"

scalaVersion := "2.11.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.3.7"
  val akkaStreamV = "1.0-M2"
  val sprayJsonV = "1.3.1"
  val slickV = "2.1.0"
  val postgresV = "9.3-1102-jdbc41"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0-M2",
    "com.typesafe.slick" %% "slick" % slickV,
    "org.postgresql" % "postgresql" % postgresV
  )
}

Revolver.settings

Revolver.enableDebugging(port = 5005, suspend = false)
