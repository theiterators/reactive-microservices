import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer

case class PasswordRegisterRequest(email: EmailAddress, password: String)
case class PasswordLoginRequest(email: EmailAddress, password: String)
case class PasswordResetRequest(email: EmailAddress, newPassword: String)

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object AuthPassword extends App with AuthPasswordJsonProtocols with AuthPasswordConfig {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val gateway = new Gateway
  val service = new AuthPasswordService(repository, gateway)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-password") {
      path("register" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordRegisterRequest]) & optionalHeaderValueByName("Auth-Token")) {
          (request, tokenValue) =>
          complete {
            service.register(request, tokenValue).map {
              case Right(identity) => ToResponseMarshallable(Created -> identity)
              case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
            }
          }
        }
      } ~
      path("login" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordLoginRequest]) & optionalHeaderValueByName("Auth-Token")) {
        (request, tokenValue) =>
          complete {
            service.login(request, tokenValue).map {
              case Right(token) => ToResponseMarshallable(Created -> token)
              case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
            }
          }
        }
      } ~
      path("reset" / "password") {
        (pathEndOrSingleSlash & post & entity(as[PasswordResetRequest]) & headerValueByName("Auth-Token")) {
          (request, tokenValue) =>
          complete {
            service.reset(request, tokenValue).map {
              case Right(identity) => ToResponseMarshallable(OK -> identity)
              case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
            }
          }
        }
      }
    }
  }
}
