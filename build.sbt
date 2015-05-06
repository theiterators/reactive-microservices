import play.PlayScala
import sbt.Keys._

name := "reactive-microservices"

organization := "com.theiterators"

version := "1.0"

lazy val `reactive-microservices` = (project in file("."))


lazy val metricsCommon = project in file("metrics-common")

lazy val `metrics-collector` = (project in file("metrics-collector")).dependsOn(metricsCommon).enablePlugins(PlayScala)

lazy val `token-manager` = (project in file("token-manager")).dependsOn(metricsCommon)

lazy val `session-manager` = project in file("session-manager")

lazy val `identity-manager` = project in file("identity-manager")

lazy val `auth-fb` = project in file("auth-fb")

lazy val `auth-codecard` = project in file("auth-codecard")

lazy val `auth-password` = project in file("auth-password")

lazy val btcCommon = project in file("btc-common")

lazy val `btc-ws` = (project in file("btc-ws")).dependsOn(btcCommon).enablePlugins(PlayScala)

lazy val `btc-users` = (project in file("btc-users")).dependsOn(btcCommon)

lazy val `frontend-server` = project in file("frontend-server")

lazy val `public-api-proxy` = project in file("publix-api-proxy")

val runAll = inputKey[Unit]("Runs all subprojects")

val compileAll = taskKey[Unit]("Compiles all subprojects")

val cleanAll = taskKey[Unit]("Cleans all subprojects")

compileAll := {
  fork in compile := true

  (compile in Compile in `frontend-server`).toTask.value
  (compile in Compile in `token-manager`).toTask.value
  (compile in Compile in `session-manager`).toTask.value
  (compile in Compile in `identity-manager`).toTask.value
  (compile in Compile in `auth-fb`).toTask.value
  (compile in Compile in `auth-codecard`).toTask.value
  (compile in Compile in `auth-password`).toTask.value
  (compile in Compile in `btc-users`).toTask.value
  (compile in Compile in `public-api-proxy`).toTask.value
}

cleanAll := {
  (clean in Compile in `frontend-server`).toTask.value
  (clean in Compile in `token-manager`).toTask.value
  (clean in Compile in `session-manager`).toTask.value
  (clean in Compile in `identity-manager`).toTask.value
  (clean in Compile in `auth-fb`).toTask.value
  (clean in Compile in `auth-codecard`).toTask.value
  (clean in Compile in `auth-password`).toTask.value
  (clean in Compile in `btc-users`).toTask.value
  (clean in Compile in `public-api-proxy`).toTask.value
}


runAll := {
  (run in Compile in `frontend-server`).evaluated
  (run in Compile in `token-manager`).evaluated
  (run in Compile in `session-manager`).evaluated
  (run in Compile in `identity-manager`).evaluated
  (run in Compile in `auth-fb`).evaluated
  (run in Compile in `auth-codecard`).evaluated
  (run in Compile in `auth-password`).evaluated
  (run in Compile in `btc-users`).evaluated
  (run in Compile in `public-api-proxy`).evaluated

}

fork in run := true

// enables unlimited amount of resources to be used :-o just for runAll convenience
concurrentRestrictions in Global := Seq(
  Tags.customLimit( _ => true) 
)
