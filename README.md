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

#### Review the configuration files

Take some time to review `application.conf` files that are located in ```resource``` subdirectory of each microservice.

#### Create and configure Postgres databases

The default names of the DBs that need to be created are: ```auth_codecard```, ```auth_password```, and ```identity_manager```. If you want to use non-default names please tweak the `application.conf` files.

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

#### Run migrations

For `auth-codecard`, `identity-manager` and `auth-password` you need to run the SQL migration scripts which are located in relevant `resources` directories.
You can also tweak and use this script in your console

```
cd /where/this/activator/template/is/located/
psql auth_codecard -U postgres -f ./auth-codecard/src/main/resources/init.sql &&
psql auth_password -U postgres -f ./auth-password/src/main/resources/auth_entry.sql &&
psql identity_manager -U postgres -f ./identity-manager/src/main/resources/identity.sql
```

#### Setup CORS proxy

Use NGINX or such to proxy with CORS headers and OPTION request responses.

Crude fast solution:

```
http {
    server {
        listen 10000;
        server_name localhost;
        set $true 1;
        more_set_headers "Access-Control-Allow-Origin: $http_origin";

        location /auth-codecard/ {
            proxy_pass http://localhost:8005;
            if ($request_method = OPTIONS ) {
                add_header Access-Control-Allow-Headers "Auth-Token, Content-Type";
                add_header Access-Control-Allow-Methods "GET, OPTIONS, POST, DELETE, PUT, PATCH";
                add_header Access-Control-Allow-Credentials "true";
                add_header Content-Length 0;
                add_header Content-Type text/plain;
                return 200;
            }
        }

        location /auth-password/ {
            proxy_pass http://localhost:8002;
            if ($request_method = OPTIONS ) {
                add_header Access-Control-Allow-Headers "Auth-Token, Content-Type";
                add_header Access-Control-Allow-Methods "GET, OPTIONS, POST, DELETE, PUT, PATCH";
                add_header Access-Control-Allow-Credentials "true";
                add_header Content-Length 0;
                add_header Content-Type text/plain;
                return 200;
            }
        }
        location /auth-fb/ {
            proxy_pass http://localhost:8001;
            if ($request_method = OPTIONS ) {
                add_header Access-Control-Allow-Headers "Auth-Token, Content-Type";
                add_header Access-Control-Allow-Methods "GET, OPTIONS, POST, DELETE, PUT, PATCH";
                add_header Access-Control-Allow-Credentials "true";
                add_header Content-Length 0;
                add_header Content-Type text/plain;
                return 200;
            }
        }
        location /session/ {
            proxy_pass http://localhost:8011;
            if ($request_method = OPTIONS ) {
                add_header Access-Control-Allow-Headers "Auth-Token, Content-Type";
                add_header Access-Control-Allow-Methods "GET, OPTIONS, POST, DELETE, PUT, PATCH";
                add_header Access-Control-Allow-Credentials "true";
                add_header Content-Length 0;
                add_header Content-Type text/plain;
                return 200;
            }
        }
        location /btc/ {
            proxy_pass http://localhost:8011;
            if ($request_method = OPTIONS ) {
                add_header Access-Control-Allow-Headers "Auth-Token, Content-Type";
                add_header Access-Control-Allow-Methods "GET, OPTIONS, POST, DELETE, PUT, PATCH, UPGRADE";
                add_header Access-Control-Allow-Credentials "true";
                add_header Content-Length 0;
                add_header Content-Type text/plain;
                return 200;
            }
        }
    }
}
```


## Running

Before starting anything make sure you have PostgreSQL, MongoDB and Redis up and running.

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
