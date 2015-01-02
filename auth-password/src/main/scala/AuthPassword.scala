import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer

case class PasswordRegisterRequest(email: EmailAddress, password: String)
case class PasswordLoginRequest(email: EmailAddress, password: String)
case class PasswordResetRequest(email: EmailAddress, password: String, newPassword: String)

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])
case class LoginRequest(identityId: Long, authMethod: String = "password")
case class ReloginRequest(tokenValue: String, authMethod: String = "password")

object AuthPassword extends App with AuthPasswordJsonProtocols with AuthPasswordConfig {

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = FlowMaterializer()
  private implicit val dispatcher = actorSystem.dispatcher

  private val gateway = new Gateway()
  private val service = new AuthPasswordService(gateway)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-password") {
      path("register" / "password") {
        pathEndOrSingleSlash {
          post {
            entity(as[PasswordRegisterRequest]) { request =>
              optionalHeaderValueByName("Auth-Token") { tokenValue =>
                complete {
                  service.register(request, tokenValue) match {
                    case Right(identity) => ToResponseMarshallable(Created -> identity)
                    case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
                  }
                }
              }
            }
          }
        }
      } ~
      path("login" / "password") {
        pathEndOrSingleSlash {
          post {
            entity(as[PasswordLoginRequest]) { request =>
              optionalHeaderValueByName("Auth-Token") { tokenValue =>
                complete {
                  service.login(request, tokenValue).map {
                    case Right(token) => ToResponseMarshallable(Created -> token)
                    case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
                  }
                }
              }
            }
          }
        }
      } ~
      path("reset" / "password") {
        pathEndOrSingleSlash {
          post {
            entity(as[PasswordResetRequest]) { request =>
              headerValueByName("Auth-Token") { tokenValue =>
                complete {
                  OK
                }
              }
            }
          }
        }
      }
    }
  }
}
