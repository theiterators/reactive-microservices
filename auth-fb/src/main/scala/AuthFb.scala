import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.http.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.restfb.DefaultFacebookClient
import com.restfb.exception.FacebookException
import com.restfb.types.User
import com.typesafe.config.ConfigFactory
import java.io.IOException
import redis.RedisClient
import scala.concurrent.{blocking, Future}
import scala.util.{Failure, Success, Try}
import spray.json.DefaultJsonProtocol

case class AuthResponse(accessToken: String, expiresIn: Long, signedRequest: String, userID: String)

case class Identity(id: Long)

case class LoginRequest(identityId: Long, authMethod: String = "fb")

case class ReloginRequest(tokenValue: String, authMethod: String = "fb")

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

trait AuthFbJsonProtocols extends DefaultJsonProtocol {
  protected implicit val authResponseFormat = jsonFormat4(AuthResponse.apply)
  protected implicit val identityFormat = jsonFormat1(Identity.apply)
  protected implicit val loginRequestFormat = jsonFormat2(LoginRequest.apply)
  protected implicit val reloginRequestFormat = jsonFormat2(ReloginRequest.apply)
  protected implicit val tokenFormat = jsonFormat4(Token.apply)
}

object AuthFb extends App with AuthFbJsonProtocols {
  private val config = ConfigFactory.load()
  private val interface = config.getString("http.interface")
  private val port = config.getInt("http.port")
  private val redisHost = config.getString("redis.host")
  private val redisPort = config.getInt("redis.port")
  private val redisPassword = config.getString("redis.password")
  private val redisDb = config.getInt("redis.db")
  private val fbAppSecret = config.getString("fb.appSecret")
  private val identityManagerHost = config.getString("services.identity-manager.host")
  private val identityManagerPort = config.getInt("services.identity-manager.port")
  private val tokenManagerHost = config.getString("services.token-manager.host")
  private val tokenManagerPort = config.getInt("services.token-manager.port")

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = FlowMaterializer()
  private implicit val dispatcher = actorSystem.dispatcher

  private val redis = RedisClient(host = redisHost, port = redisPort, password = Option(redisPassword), db = Option(redisDb))

  private val identityManagerConnection = Http().outgoingConnection(identityManagerHost, identityManagerPort)
  private val tokenManagerConnection = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  private def requestIdentityManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(identityManagerConnection.flow).runWith(Sink.head)
  }

  private def requestTokenManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(tokenManagerConnection.flow).runWith(Sink.head)
  }

  private def requestNewIdentity(): Future[Identity] = {
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Identity]
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Identity request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  private def requestToken(tokenValue: String): Future[Either[String, Token]] = {
    requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue")).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Token].map(Right(_))
      } else if (response.status == NotFound) {
        Future.successful(Left("Token expired or not found"))
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Token request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  private def requestLogin(identityId: Long): Future[Token] = {
    val loginRequest = LoginRequest(identityId)
    requestTokenManager(RequestBuilding.Post("/tokens", loginRequest)).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Token]
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Login request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  private def requestRelogin(tokenValue: String): Future[Option[Token]] = {
    val reloginRequest = ReloginRequest(tokenValue)
    requestTokenManager(RequestBuilding.Patch("/tokens", reloginRequest)).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Token].map(Option(_))
      } else if (response.status == NotFound) {
        Future.successful(None)
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Relogin request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  private def userToRedisKey(user: User): String = s"auth-fb:id:${user.getId}"

  private def getIdentityIdForUser(user: User): Future[Option[Long]] = redis.get(userToRedisKey(user)).map(_.map(_.utf8String.toLong))

  private def getFbUserDetails(accessToken: String): Try[User] = {
    Try {
      blocking {
        val client = new DefaultFacebookClient(accessToken, fbAppSecret)
        client.fetchObject("me", classOf[User])
      }
    }
  }

  private def register(user: User, tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    val key = userToRedisKey(user)
    redis.exists(key).flatMap { exists =>
      if (exists) {
        Future.successful(Left(s"User with id ${user.getId} is already registered"))
      } else {
        val identityEitherFuture: Future[Either[String, Identity]] = tokenValueOption match {
          case Some(tokenValue) => requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
          case None => requestNewIdentity().map(Right(_))
        }

        identityEitherFuture.flatMap {
          case Right(identity) =>
            redis.setnx(key, identity.id).map(set => if (set) Right(identity) else Left(s"User with id ${user.getId} is already registered"))
          case l => Future.successful(l)
        }
      }
    }
  }

  private def login(user: User, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    getIdentityIdForUser(user).flatMap {
      case Some(identityId) =>
        tokenValueOption match {
          case Some(tokenValue) =>
            requestRelogin(tokenValue).map {
              case Some(token) => Right(token)
              case None => Left("Token expired or not found")
            }
          case None => requestLogin(identityId).map(Right(_))
        }
      case None => Future.successful(Left(s"User with id ${user.getId} is not registered"))
    }
  }

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("auth-fb") {
      path("register" / "fb") {
        pathEndOrSingleSlash {
          post {
            entity(as[AuthResponse]) { authResponse =>
              optionalHeaderValueByName("Auth-Token") { tokenValue =>
                complete {
                  getFbUserDetails(authResponse.accessToken) match {
                    case Success(user) => register(user, tokenValue).map {
                      case Right(identity) => ToResponseMarshallable(Created -> identity)
                      case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
                    }
                    case Failure(e: FacebookException) => Unauthorized -> e.getMessage
                    case _ => InternalServerError
                  }
                }
              }
            }
          }
        }
      } ~
      path("login" / "fb") {
        pathEndOrSingleSlash {
          post {
            entity(as[AuthResponse]) { authResponse =>
              optionalHeaderValueByName("Auth-Token") { tokenValue =>
                complete {
                  getFbUserDetails(authResponse.accessToken) match {
                    case Success(user) =>
                      login(user, tokenValue).map {
                        case Right(token) => ToResponseMarshallable(Created -> token)
                        case Left(errorMessage) => ToResponseMarshallable(BadRequest -> errorMessage)
                      }
                    case Failure(e: FacebookException) => Unauthorized -> e.getMessage
                    case _ => InternalServerError
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
