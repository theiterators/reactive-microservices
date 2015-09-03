# Reactive microservices

[![Join the chat at https://gitter.im/theiterators/reactive-microservices](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/theiterators/reactive-microservices?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Reactive microservices is an Typesafe Activator Template completely devoted to microservices architecture. It lets you learn about microservices in general - different patterns, communication protocols and 'tastes' of microservices. All these concepts are demonstrated using Scala, Akka, Play and other tools from Scala ecosystem. For the sake of clarity, we skipped topics related to deployment and operations.

## Prerequisites

To feel comfortable while playing with this template, make sure you know basics of Akka HTTP which is a cornerstone of this project. We recently released an [Akka HTTP activator template](https://typesafe.com/activator/template/akka-http-microservice) that may help you start. At least brief knowledge of [Akka remoting](https://typesafe.com/activator/template/akka-sample-remote-scala), [Akka persistence](https://typesafe.com/activator/template/akka-sample-persistence-scala), [Akka streams](https://typesafe.com/activator/template/akka-stream-scala) and [Play Framework websockets](https://typesafe.com/activator/template/anonymous-chat) is also highly recommended.

## Structure

This activator template consists of 9 runnable subprojects — the microservices:
 * auth ones:
   * `auth-codecard`
   * `auth-fb`
   * `auth-password`
   * `identity-manager`
   * `session-manager`
   * `token-manager`
 * business logic ones:
   * `btc-users`
   * `btc-ws`
 * miscellaneous ones:
   * `metrics-collector`
   
They uses different communication methods, different databases, and different frameworks.

## Setup

#### Review the configuration files

Take some time to review `application.conf` files that are located in ```resource``` subdirectory of each microservice. You can also look at `docker-compose.yml` file, which contains docker preconfigurated images for all the required databases.

#### Run migrations (You don't need to do this step if you want to use our docker container)

For `auth-codecard`, `identity-manager` and `auth-password` you need to run the SQL migration scripts which are located in `postgres` directory. If you want to use non-default names please tweak the `application.conf` files.
You can also tweak and use this script in your console.

```
cd /where/this/activator/template/is/located/
psql -h localhost -U postgres -f ./postgres/init.sql &&
psql -h localhost -U postgres -f ./postgres/auth_entry.sql &&
psql -h localhost -U postgres -f ./postgres/identity.sql
```

## Running

Run `docker-compose up` in project main directory to launch databases, or if you are using your own database instances, make sure you have PostgreSQL, MongoDB and Redis up and running.

#### akka-http
You can run each service separately, but we also we provided a SBT task called `runAll`.

#### Play
Due to some issues with Play/sbt cooperation `metrics-collector` and `btc-ws` should be run separately.
In order to run them in one sbt CLI instance use these commands:
```
; project btc-ws; run 9000
```
```
; project metrics-collector; run 5001
```

Everything else should work out of the box. Enjoy!

## Author & license

If you have any questions regarding this project contact: 

Łukasz Sowa <lukasz@iterato.rs> from [Iterators](http://iterato.rs).

For licensing info see LICENSE file in project's root directory.
