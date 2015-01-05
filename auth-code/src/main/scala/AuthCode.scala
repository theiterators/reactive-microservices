import java.io.IOException
import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.http.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import spray.json._
import scala.concurrent.{ExecutionContext, Future}
import scala.slick.lifted.{ProvenShape, Tag}
import scala.util.Random
import scala.slick.driver.PostgresDriver.simple._

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])
case class CodeCard(id: Long, codes: Seq[String])
case class AuthEntry(userIdentifier : String, identityId: Long, createdAt: Long, lastCard: Long)
case class Code(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String, createdAt: Long, activatedAt: Option[Long] = None,usedAt: Option[Long] = None)

case class RegisterResponse(identity: Identity, userIdentifier: String, codesCard: CodeCard)
case class LoginRequest(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String)
case class ActivateCodeRequest(userIdentifier: String)
case class ActivateCodeResponse(cardIndex: Long, codeIndex: Long)
case class GetCodeCardRequest(userIdentifier: String)
case class GetCodeCardResponse(userIdentifier: String, codesCard: CodeCard)



class AuthEntries(tag: Tag) extends Table[AuthEntry](tag, "auth_entry") {
  def userIdentifier = column[String]("user_identifier", O.NotNull)
  def identityId = column[Long]("identity_id", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def lastCard = column[Long]("last_card")
  override def * : ProvenShape[AuthEntry] = (userIdentifier,identityId, createdAt,lastCard) <>((AuthEntry.apply _).tupled, AuthEntry.unapply)
}
class Codes(tag: Tag) extends Table[Code](tag, "code") {
  def userIdentifier = column[String]("user_identifier", O.NotNull)
  def cardIndex = column[Long]("card_index", O.NotNull)
  def codeIndex = column[Long]("code_index", O.NotNull)
  def code = column[String]("code", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def activatedAt = column[Option[Long]]("activated_at")
  def usedAt = column[Option[Long]]("used_at")
  override def * : ProvenShape[Code] = (userIdentifier, cardIndex, codeIndex, code, createdAt, activatedAt,usedAt) <>((Code.apply _).tupled, Code.unapply)
}

object AuthCode extends App with AuthCodeJsonProtocol {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val dbUrl = config.getString("db.url")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")

  val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
  val authEntriesQuery = TableQuery[AuthEntries]
  val codesQuery = TableQuery[Codes]

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
          val card = generateCodeCard(1,authEntry.userIdentifier)
          Right(RegisterResponse(identity, authEntry.userIdentifier, card))
        case Left(l) => Left(l)
      }
    }

  def activateCode(request: ActivateCodeRequest)(implicit ec: ExecutionContext): Future[Either[String,ActivateCodeResponse]] = {
    Future {
      db.withSession { implicit s =>
        s.withTransaction {
          val codes = codesQuery.filter(code => (code.userIdentifier === request.userIdentifier && code.activatedAt.isEmpty === true)).list
          codes.length match {
            case 0 => Left("You don't have available codes")
            case _ =>
              val rand = Random.nextInt(codes.length)
              val codeAct = codes(rand)
              codesQuery.filter(code => (code.userIdentifier === request.userIdentifier && code.cardIndex === codeAct.cardIndex && code.codeIndex ===  codeAct.codeIndex)).map(_.activatedAt).update(Some(System.currentTimeMillis))
              Right(ActivateCodeResponse(codeAct.cardIndex, codeAct.codeIndex))
          }
        }
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

  def getCodeCard(request:GetCodeCardRequest, tokenValueOption: Option[String]): Future[Either[String, GetCodeCardResponse]] =
      tokenValueOption match {
        case Some(tokenValue) =>
          gateway.requestRelogin(tokenValue).map {
            case None => Left("Token expired or not found")
            case Some(token) if (repository.getIdentity(request.userIdentifier) == token.identityId) =>
              Right(GetCodeCardResponse(request.userIdentifier, generateCodeCard(repository.getNextCardIndex(request.userIdentifier), request.userIdentifier)))
            //TODO ten case wykona się jezeli zalogowany user będzie próbował pobrać kody innego user, gdybyśmy dali tu komunikat "nie masz uprawnień" da sie odkryć jakie konta istnieją w systemie
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

  private def generateUserIdentifier = f"${Random.nextInt(100000)}%05d${Random.nextInt(100000)}%05d"

  private def generateAuthEntry(identity: Identity) = db.withSession { implicit s =>
    val authEntry = AuthEntry(generateUserIdentifier,identity.id,System.currentTimeMillis(),1)
    authEntriesQuery += authEntry
    authEntry
  }

  private def generateCodeCard(cardIndex:Long,userIdentifier:String) = db.withSession { implicit s =>
    CodeCard(cardIndex, Seq.fill(20) {f"${Random.nextInt(1000000)}%06d"}.zipWithIndex.map { case (code,idx) =>
      codesQuery +=  Code(userIdentifier,cardIndex,idx.toLong,code,System.currentTimeMillis())
      code
    })
  }
}



class Repository(config: Config) {
  val dbUrl = config.getString("db.url")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")
  val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
  val codesQuery = TableQuery[Codes]
  val authEntriesQuery = TableQuery[AuthEntries]


  def useCode(userIdentifier: String, cardIdx: Long, codeIdx: Long, code: String): Int =
    db.withSession { implicit s =>
      codesQuery.filter(codeQ => (codeQ.userIdentifier === userIdentifier && codeQ.cardIndex === cardIdx && codeQ.codeIndex === codeIdx && codeQ.code === code && codeQ.usedAt.isEmpty === true))
        .map(_.usedAt).update(Some(System.currentTimeMillis))
    }

  def getIdentity(userIdentifier: String) =
    db.withSession { implicit s =>
      authEntriesQuery.filter(line => (line.userIdentifier === userIdentifier)).map(_.identityId).first
    }

  def getNextCardIndex(userIdentifier: String): Long =
    db.withSession { implicit s =>
      val next = authEntriesQuery.filter(line => (line.userIdentifier === userIdentifier)).map(_.lastCard).first + 1
      authEntriesQuery.filter(line => (line.userIdentifier === userIdentifier)).map(_.lastCard).update(next)
      next
    }

}