import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer

case class CodeCard(id: Long, codes: Seq[String], userIdentifier: String)
case class RegisterResponse(identity: Identity, codesCard: CodeCard)
case class LoginRequest(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String)
case class ActivateCodeRequest(userIdentifier: String)
case class ActivateCodeResponse(cardIndex: Long, codeIndex: Long)
case class GetCodeCardRequest(userIdentifier: String)
case class GetCodeCardResponse(userIdentifier: String, codesCard: CodeCard)

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object AuthCodeCardCard extends App with JsonProtocols with Config {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val gateway = new Gateway
  val service = new Service(gateway, repository)

  Http().bindAndHandle(interface = interface, port = port, handler = {
    logRequestResult("auth-codecard") {
      (path("register" / "codecard" ) & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token")) { (tokenValue) =>
        complete {
          service.register(tokenValue).map[ToResponseMarshallable] {
            case Right(response) => Created -> response
            case Left(errorMessage) => BadRequest -> errorMessage
          }
        }
      } ~
      (path("login" / "codecard" / "activate") & pathEndOrSingleSlash & post & entity(as[ActivateCodeRequest])) { (request) =>
        complete {
          service.activateCode(request).map[ToResponseMarshallable] {
            case Right(response) => OK -> response
            case Left(errorMessage) => BadRequest -> errorMessage
          }
        }
      } ~
      (path("login" / "codecard") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[LoginRequest])) { (tokenValue, request) =>
        complete {
          service.login(request, tokenValue).map[ToResponseMarshallable] {
            case Right(response) => Created -> response
            case Left(errorMessage) => BadRequest -> errorMessage
          }
        }
      } ~
      (path("generate" / "codecard") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[GetCodeCardRequest])) { (tokenValue, request) =>
        complete {
          service.getCodeCard(request, tokenValue).map[ToResponseMarshallable] {
            case Right(response) => OK -> response
            case Left(errorMessage) => BadRequest -> errorMessage
          }
        }
      }
    }
  })
}
