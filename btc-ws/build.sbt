name := "btc-ws"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

lazy val `btc-ws` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= {
  Seq(
    ws
  )
}