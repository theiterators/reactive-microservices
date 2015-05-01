import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import com.restfb.exception.FacebookException
import scala.util.{Failure => FailureT, Success => SuccessT}

case class AuthResponse(accessToken: String)

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object AuthFb extends App with JsonProtocols with Config {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val gateway = new Gateway
  val service = new Service(gateway)

  Http().bindAndHandle(interface = interface, port = port, handler = {
    logRequestResult("auth-fb") {
      (path("register" / "fb") & pathEndOrSingleSlash & post & entity(as[AuthResponse]) & optionalHeaderValueByName("Auth-Token")) { (authResponse, tokenValue) =>
        complete {
          service.register(authResponse, tokenValue) match {
            case SuccessT(f) => f.map[ToResponseMarshallable] {
              case Right(identity) => Created -> identity
              case Left(errorMessage) => BadRequest -> errorMessage
            }
            case FailureT(e: FacebookException) => Unauthorized -> e.getMessage
            case _ => InternalServerError
          }
        }
      } ~
      (path("login" / "fb") & pathEndOrSingleSlash & post & entity(as[AuthResponse]) & optionalHeaderValueByName("Auth-Token")) { (authResponse, tokenValue) =>
        complete {
          service.login(authResponse, tokenValue) match {
            case SuccessT(f) => f.map[ToResponseMarshallable] {
              case Right(token) => Created -> token
              case Left(errorMessage) => BadRequest -> errorMessage
            }
            case FailureT(e: FacebookException) => Unauthorized -> e.getMessage
            case _ => InternalServerError
          }
        }
      }
    }
  })
}