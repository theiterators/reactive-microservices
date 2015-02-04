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

object TokenManager extends App with JsonProtocols with Config {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val service = new Service(repository)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("token-manager") {
      pathPrefix("tokens") {
        (post & pathEndOrSingleSlash & entity(as[LoginRequest])) { loginRequest =>
          complete {
            service.login(loginRequest).map(token => Created -> token)
          }
        } ~
        (patch & pathEndOrSingleSlash & entity(as[ReloginRequest])) { reloginRequest =>
          complete {
            service.relogin(reloginRequest).map[ToResponseMarshallable] {
              case Some(token) => OK -> token
              case None => NotFound -> "Token expired or not found"
            }
          }
        } ~
        (path(Segment) & pathEndOrSingleSlash) { tokenValue =>
          get {
            complete {
              service.findAndRefreshToken(tokenValue).map[ToResponseMarshallable] {
                case Some(token) => OK -> token
                case None => NotFound -> "Token expired or not found"
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
}

