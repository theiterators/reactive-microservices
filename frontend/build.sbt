enablePlugins(ScalaJSPlugin)

name := "Scala.js Tutorial"

//https://github.com/nodejs/node-v0.x-archive/wiki/Installing-Node.js-via-package-manager

scalaVersion := "2.11.7" // or any other Scala version >= 2.10.2

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.8.0"

libraryDependencies += "be.doeraene" %%% "scalajs-jquery" % "0.8.0"

libraryDependencies += "com.lihaoyi" %%% "upickle" % "0.3.6"

skip in packageJSDependencies := false

scalaJSStage in Global := FastOptStage