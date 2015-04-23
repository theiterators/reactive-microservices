# Reactive microservices

Reactive microservices is an Typesafe Activator Template completely devoted to microservices architecture. It lets you learn about microservices in general - different patterns, communication protocols and 'tastes' of microservices. All these concepts are demonstrated using Scala, Akka, Play and other tools from Scala ecosystem. For the sake of clarity, we skipped topics related to deployment and operations.

## Prerequisites

To feel comfortable while playing with this template, make sure you know basics of Akka HTTP which is a cornerstone of this project. We recently released an [Akka HTTP activator template](https://typesafe.com/activator/template/akka-http-microservice) that may help you start. At least brief knowledge of [Akka remoting](https://typesafe.com/activator/template/akka-sample-remote-scala), [Akka persistence](https://typesafe.com/activator/template/akka-sample-persistence-scala), [Akka streams](https://typesafe.com/activator/template/akka-stream-scala) and [Play Framework websockets](https://typesafe.com/activator/template/anonymous-chat) is also highly recommended.

## Notes on running

This activator template consists of 10 runnable subprojects (`auth-codecard`, `auth-fb`, `auth-password`, `btc-users`, `btc-ws`, `frontend-server`, `identity-manager`, `session-manager`, `metrics-collector`, `token-manager`). Running them separately, one by one, is of course possible but for your convenience we provided a SBT task called `runAll`. However, due to some issues with Play/sbt cooperation `metrics-collector` and `btc-ws` should be run separately like this: `; project btc-ws; run 9000`, `; project metrics-collector; run 5001`. Before starting anything make sure you have PostgreSQL, MongoDB and Redis up and running. Also take some time to review `application.conf` files - database configurations may require little tweaking. For `auth-codecard`, `identity-manager` and `auth-password` you need to manually run migration SQL scripts which are located in relevant `resources` directories. Everything else should work out of the box. Enjoy!

## Author & license

If you have any questions regarding this project contact:

Łukasz Sowa <lukasz@iterato.rs> from [Iterators](http://iterato.rs).

For licensing info see LICENSE file in project's root directory.