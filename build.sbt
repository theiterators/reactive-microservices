import play.PlayScala

name := "reactive-microservices"

organization := "com.theiterators"

version := "1.0"

lazy val `reactive-microservices` = project in file(".")

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
