import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer

case class PasswordRegisterRequest(email: EmailAddress, password: String)
case class PasswordLoginRequest(email: EmailAddress, password: String)
case class PasswordResetRequest(email: EmailAddress, newPassword: String)

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object AuthPassword extends App with JsonProtocols with Config {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val gateway = new Gateway
  val service = new Service(repository, gateway)

  Http().bindAndHandle(interface = interface, port = port, handler = {
    logRequestResult("auth-password") {
      path("register" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordRegisterRequest]) & optionalHeaderValueByName("Auth-Token")) {
          (request, tokenValue) =>
          complete {
            service.register(request, tokenValue).map[ToResponseMarshallable] {
              case Right(identity) => Created -> identity
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        }
      } ~
      path("login" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordLoginRequest]) & optionalHeaderValueByName("Auth-Token")) {
        (request, tokenValue) =>
          complete {
            service.login(request, tokenValue).map[ToResponseMarshallable] {
              case Right(token) => Created -> token
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        }
      } ~
      path("reset" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordResetRequest]) & headerValueByName("Auth-Token")) {
          (request, tokenValue) =>
          complete {
            service.reset(request, tokenValue).map[ToResponseMarshallable] {
              case Right(identity) => OK -> identity
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        }
      }
    }
  })
}
