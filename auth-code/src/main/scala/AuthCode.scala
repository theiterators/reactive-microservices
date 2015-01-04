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
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import spray.json._
import scala.concurrent.{ExecutionContext, Future}
import scala.slick.lifted.{ProvenShape, Tag}
import scala.util.Random
import scala.slick.driver.PostgresDriver.simple._

case class Identity(id: Long)
case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])
case class CodeCard(id: Long, codes: Seq[String])
case class RegisterResponse(identity: Identity, userIdentifier: String, codesCard: CodeCard)
case class AuthEntry(userIdentifier : String, identityId: Long, createdAt: Long, lastCard: Option[Long] = None)
case class Code(userIdentifier: String, cardIndex: Long, code: String, createdAt: Long, activatedAt: Option[Long] = None)


class AuthEntries(tag: Tag) extends Table[AuthEntry](tag, "auth_entry") {
  def userIdentifier = column[String]("user_identifier", O.NotNull)
  def identityId = column[Long]("identity_id", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def lastCard = column[Option[Long]]("last_card")
  override def * : ProvenShape[AuthEntry] = (userIdentifier,identityId, createdAt,lastCard) <>((AuthEntry.apply _).tupled, AuthEntry.unapply)
}
class Codes(tag: Tag) extends Table[Code](tag, "code") {
  def userIdentifier = column[String]("user_identifier", O.NotNull)
  def cardIndex = column[Long]("card_index", O.NotNull)
  def code = column[String]("code", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def activatedAt = column[Option[Long]]("activated_at")
  override def * : ProvenShape[Code] = (userIdentifier, cardIndex, code, createdAt, activatedAt) <>((Code.apply _).tupled, Code.unapply)
}

object AuthCode extends App with DefaultJsonProtocol {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val dbUrl = config.getString("db.url")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")

  val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
  val authEntries = TableQuery[AuthEntries]
  val codes = TableQuery[Codes]


  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher
  implicit val identityFormat = jsonFormat1(Identity)
  implicit val tokenFormat = jsonFormat4(Token)
  implicit val codeCardFormat = jsonFormat2(CodeCard.apply)
  implicit val registerResponse = jsonFormat3(RegisterResponse)
  implicit val AuthEntryFormat = jsonFormat4(AuthEntry)

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-code") {
      path("register") {
        pathEndOrSingleSlash {
          post {
            optionalHeaderValueByName("Auth-Token") { tokenValue =>
              complete {
                register(tokenValue)
              }
            }
          }
        }
      } ~
        path("login") {
          pathEndOrSingleSlash {
            get {
              optionalHeaderValueByName("Auth-Token") { tokenValue =>
                complete {
                  OK -> "wooo"
                }
              }
            }
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


  private def acquireIdentity(tokenValueOption: Option[String])(implicit ec: ExecutionContext): Future[Either[String, Identity]] =
    tokenValueOption match {
      case Some(tokenValue) => requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
      case None => requestNewIdentity().map(Right(_))
    }


  val identityManagerHost = config.getString("services.identity-manager.host")
  val identityManagerPort = config.getInt("services.identity-manager.port")
  val tokenManagerHost = config.getString("services.token-manager.host")
  val tokenManagerPort = config.getInt("services.token-manager.port")
  val identityManagerConnection = Http().outgoingConnection(identityManagerHost, identityManagerPort)
  val tokenManagerConnection = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  private def requestIdentityManager(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(identityManagerConnection.flow).runWith(Sink.head)

  private def requestTokenManager(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(tokenManagerConnection.flow).runWith(Sink.head)

  def requestToken(tokenValue: String)(implicit ec: ExecutionContext): Future[Either[String, Token]] =
    requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue")).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Token].map(Right(_))
        case NotFound => Future.successful(Left("Invalid token"))
        case status => Future.failed(new IOException(s"Token request failed with status ${response.status} and error ${response.entity.toString}")) //fixme
      }
    }

  def requestNewIdentity()(implicit ec: ExecutionContext): Future[Identity] =
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Identity]
        case status => Future.failed(new IOException(s"Identity request failed with status ${response.status} and error ${response.entity.toString}")) //fixme
      }
    }


  //  def requestLogin(identityId: Long): Future[Token] = {
  //    val loginRequest = LoginRequest(identityId)
  //    requestTokenManager(RequestBuilding.Post("/tokens", loginRequest)).flatMap { response =>
  //      if (response.status.isSuccess()) {
  //        Unmarshal(response.entity).to[Token]
  //      } else {
  //        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
  //          Future.failed(new IOException(s"Login request failed with status ${response.status} and error $errorMessage"))
  //        }
  //      }
  //    }
  //  }
  //
  //  def requestRelogin(tokenValue: String): Future[Option[Token]] = {
  //    val reloginRequest = ReloginRequest(tokenValue)
  //    requestTokenManager(RequestBuilding.Patch("/tokens", reloginRequest)).flatMap { response =>
  //      if (response.status.isSuccess()) {
  //        Unmarshal(response.entity).to[Token].map(Option(_))
  //      } else if (response.status == NotFound) {
  //        Future.successful(None)
  //      } else {
  //        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
  //          Future.failed(new IOException(s"Relogin request failed with status ${response.status} and error $errorMessage"))
  //        }
  //      }
  //    }
  //  }


  def generateUserIdentifier = f"${Random.nextInt(100000)}%05d${Random.nextInt(100000)}%05d"

  def generateAuthEntry(identity: Identity) = db.withSession { implicit s =>
    val authEntry = AuthEntry(generateUserIdentifier,identity.id,System.currentTimeMillis())
    authEntries += authEntry
    authEntry
  }

  def generateCodeCard(index:Long,userIdentifier:String) = db.withSession { implicit s =>
    CodeCard(index, Seq.fill(20) {f"${Random.nextInt(1000000)}%06d"} map { code =>
      codes +=  Code(userIdentifier,index,code,System.currentTimeMillis())
      code
    })
  }
}