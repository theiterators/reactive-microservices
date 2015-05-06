# Reactive microservices

Reactive microservices is an Typesafe Activator Template completely devoted to microservices architecture. It lets you learn about microservices in general - different patterns, communication protocols and 'tastes' of microservices. All these concepts are demonstrated using Scala, Akka, Play and other tools from Scala ecosystem. For the sake of clarity, we skipped topics related to deployment and operations.

## Prerequisites

To feel comfortable while playing with this template, make sure you know basics of Akka HTTP which is a cornerstone of this project. We recently released an [Akka HTTP activator template](https://typesafe.com/activator/template/akka-http-microservice) that may help you start. At least brief knowledge of [Akka remoting](https://typesafe.com/activator/template/akka-sample-remote-scala), [Akka persistence](https://typesafe.com/activator/template/akka-sample-persistence-scala), [Akka streams](https://typesafe.com/activator/template/akka-stream-scala) and [Play Framework websockets](https://typesafe.com/activator/template/anonymous-chat) is also highly recommended.

## Structure

This activator template consists of 10 runnable subprojects — the microservices:
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
   * `frontend-server`
   
They uses different communication methods, different databases, and different frameworks.

## Setup

### Review the configuration files

Also take some time to review `application.conf` files - database configurations may require little tweaking.

### Create and configure Postgres databases

The default names of the DBs that need to be created are: ```auth_codecard```, ```auth_password```, and ```identity_manager```.

Scripts for quick setup:
```
createdb auth_codecard -U postgres
```
```
createdb auth_password -U postgres
```
```
createdb identity_manager -U postgres
```

### Run migrations

For `auth-codecard`, `identity-manager` and `auth-password` you need to run the SQL migration scripts which are located in relevant `resources` directories.
You can also tweak and use this script in your console

```
cd /folder/where/activator/is/located/
psql auth_codecard -U postgres -F ./auth-codecard/src/main/resources/init.sql
psql auth_password -U postgres -F ./auth-password/src/main/resources/auth_entry.sql
psql identity_manager -U postgres -F ./identity-manager/src/main/resources/identity.sql
```

## Running

Before starting anything make sure you have PostgreSQL, MongoDB and Redis up and running.

### akka-http
You can run each service separately, but we also we provided a SBT task called `runAll`.

### Play
Due to some issues with Play/sbt cooperation `metrics-collector` and `btc-ws` should be run separately.
In order to run them in one sbt CLI instance use these commands:
`; project btc-ws; run 9000`, `; project metrics-collector; run 5001`

Everything else should work out of the box. Enjoy!

## Author & license

If you have any questions regarding this project contact: 

Łukasz Sowa <lukasz@iterato.rs> from [Iterators](http://iterato.rs).

For licensing info see LICENSE file in project's root directory.
