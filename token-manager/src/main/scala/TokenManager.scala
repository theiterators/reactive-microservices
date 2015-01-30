import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer

case class LoginRequest(identityId: Long, authMethod: String)
case class ReloginRequest(tokenValue: String, authMethod: String)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object TokenManager extends App with TokenManagerJsonProtocols with TokenManagerConfig {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val service = new TokenManagerService(repository)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("token-manager") {
      (pathPrefix("tokens") & pathEndOrSingleSlash) {
        (post & entity(as[LoginRequest])) { loginRequest =>
          complete {
            service.login(loginRequest).map(token => ToResponseMarshallable(Created -> token))
          }
        } ~
        (patch & entity(as[ReloginRequest])) { reloginRequest =>
          complete {
            service.relogin(reloginRequest).map {
              case Some(token) => ToResponseMarshallable(OK -> token)
              case None => ToResponseMarshallable(NotFound -> "Token expired or not found")
            }
          }
        }
      } ~
      (path(Segment) & pathEndOrSingleSlash) { tokenValue =>
        get {
          complete {
            service.findAndRefreshToken(tokenValue).map {
              case Some(token) => ToResponseMarshallable(OK -> token)
              case None => ToResponseMarshallable(NotFound -> "Token expired or not found")
            }
          }
        } ~
        delete {
          complete {
            service.logout(tokenValue)
            OK
          }
        }
      }
    }
  }
}
