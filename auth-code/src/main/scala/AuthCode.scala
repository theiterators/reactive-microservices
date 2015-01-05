import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer
import com.typesafe.config.ConfigFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])
case class CodeCard(id: Long, codes: Seq[String], userIdentifier: String)
case class RegisterResponse(identity: Identity, codesCard: CodeCard)
case class LoginRequest(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String)
case class ActivateCodeRequest(userIdentifier: String)
case class ActivateCodeResponse(cardIndex: Long, codeIndex: Long)
case class GetCodeCardRequest(userIdentifier: String)
case class GetCodeCardResponse(userIdentifier: String, codesCard: CodeCard)


object AuthCode extends App with AuthCodeJsonProtocol {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher
  val repository = new Repository(config)
  val gateway = new Gateway(config)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-code") {
      (path("register") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token")) { (tokenValue) =>
        complete {
          register(tokenValue)
        }
      } ~
        (path("login" / "activate") & pathEndOrSingleSlash & post & entity(as[ActivateCodeRequest])) { (request) =>
          complete {
            activateCode(request)
          }
        } ~
        (path("login") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[LoginRequest])) { (tokenValue, request) =>
          complete {
            login(request, tokenValue)
          }
        } ~ (path("codes") & pathEndOrSingleSlash & post & optionalHeaderValueByName("Auth-Token") & entity(as[GetCodeCardRequest])) { (tokenValue, request) =>
        complete {
          getCodeCard(request, tokenValue)
        }
      }
    }
  }

  def register(tokenValueOption: Option[String])(implicit ec: ExecutionContext): Future[Either[String, RegisterResponse]] =
    acquireIdentity(tokenValueOption).map {
      _ match {
        case Right(identity) =>
          val authEntry = generateAuthEntry(identity)
          repository.saveAuthEntry(authEntry)
          val codeCard = generateCodeCard(1, authEntry.userIdentifier)
          repository.saveCodeCard(codeCard)
          Right(RegisterResponse(identity, codeCard))
        case Left(l) => Left(l)
      }
    }

  def activateCode(request: ActivateCodeRequest)(implicit ec: ExecutionContext): Future[Either[String, ActivateCodeResponse]] = {
    Future {
      val codes = repository.getInactiveCodesForUser(request.userIdentifier)
      codes.length match {
        case 0 => Left("You don't have available codes")
        case _ =>
          val codeAct = codes(Random.nextInt(codes.length))
          repository.activateCode(request.userIdentifier, codeAct.cardIndex, codeAct.codeIndex)
          Right(ActivateCodeResponse(codeAct.cardIndex, codeAct.codeIndex))
      }
    }
  }

  def login(request: LoginRequest, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    repository.useCode(request.userIdentifier, request.cardIndex, request.codeIndex, request.code) match {
      case 1 =>
        tokenValueOption match {
          case None => gateway.requestLogin(repository.getIdentity(request.userIdentifier)).map(Right(_))
          case Some(tokenValue) => {
            gateway.requestRelogin(tokenValue).map {
              case Some(token) => Right(token)
              case None => Left("Token expired or not found")
            }
          }
        }
      case 0 => Future.successful(Left(s"Invalid code"))
    }
  }

  def getCodeCard(request: GetCodeCardRequest, tokenValueOption: Option[String]): Future[Either[String, GetCodeCardResponse]] =
    tokenValueOption match {
      case Some(tokenValue) =>
        gateway.requestRelogin(tokenValue).map {
          case None => Left("Token expired or not found")
          case Some(token) if (repository.getIdentity(request.userIdentifier) == token.identityId) =>
            Right(GetCodeCardResponse(request.userIdentifier, generateCodeCard(repository.getNextCardIndex(request.userIdentifier), request.userIdentifier)))
          case Some(token) => Left("Token expired or not found")
        }
      case None => Future {
        Left("Token expired or not found")
      }
    }


  private def acquireIdentity(tokenValueOption: Option[String])(implicit ec: ExecutionContext): Future[Either[String, Identity]] =
    tokenValueOption match {
      case Some(tokenValue) => gateway.requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
      case None => gateway.requestNewIdentity.map(Right(_))
    }

  private def generateAuthEntry(identity: Identity) =
    AuthEntry(f"${Random.nextInt(100000)}%05d${Random.nextInt(100000)}%05d", identity.id, System.currentTimeMillis(), 1)

  private def generateCodeCard(cardIndex: Long, userIdentifier: String) =
    CodeCard(cardIndex, Seq.fill(20) {f"${Random.nextInt(1000000)}%06d" }, userIdentifier)

}


