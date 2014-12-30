import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
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
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import spray.json.DefaultJsonProtocol

case class AuthResponse(accessToken: String, expiresIn: Long, signedRequest: String, userID: String)

object AuthResponse extends DefaultJsonProtocol {
  implicit val jsonFormat = jsonFormat4(AuthResponse.apply)
}

case class Identity(id: Long)

object Identity extends DefaultJsonProtocol {
  implicit val identityFormat = jsonFormat1(Identity.apply)
}

case class LoginRequest(identityId: Long, authMethod: String = "fb")

object LoginRequest extends DefaultJsonProtocol {
  implicit val loginRequestFormat = jsonFormat2(LoginRequest.apply)
}

case class ReloginRequest(tokenValue: String, authMethod: String = "fb")

object ReloginRequest extends DefaultJsonProtocol {
  implicit val reloginRequestFormat = jsonFormat2(ReloginRequest.apply)
}

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object Token extends DefaultJsonProtocol {
  implicit val tokenFormat = jsonFormat4(Token.apply)
}

object AuthFb extends App {
  private val config = ConfigFactory.load()
  private val interface = config.getString("http.interface")
  private val port = config.getInt("http.port")
  private val redisHost = config.getString("redis.host")
  private val redisPort = config.getInt("redis.port")
  private val redisPassword = config.getString("redis.password")
  private val redisDb = config.getInt("redis.db")
  private val fbAppSecret = config.getString("fb.appSecret")

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = FlowMaterializer()
  private implicit val dispatcher = actorSystem.dispatcher

  private val redis = RedisClient(host = redisHost, port = redisPort, password = Option(redisPassword), db = Option(redisDb))

  private def getFbUserDetails(accessToken: String): Try[User] = {
    Try {
      val client = new DefaultFacebookClient(accessToken, fbAppSecret)
      client.fetchObject("me", classOf[User])
    }
  }

  private def requestNewIdentity(): Future[Identity] = {
    val connection = Http().outgoingConnection("localhost", 8000)
    val responseFuture = Source.single(RequestBuilding.Post("/identities")).via(connection.flow).runWith(Sink.head)
    responseFuture.flatMap { response =>
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
    val connection = Http().outgoingConnection("localhost", 8010)
    val responseFuture = Source.single(RequestBuilding.Get(s"/tokens/$tokenValue")).via(connection.flow).runWith(Sink.head)
    responseFuture.flatMap { response =>
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

  private def userToRedisKey(user: User): String = s"auth-fb:id:${user.getId}"

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

  private def getIdentityIdFromUser(user: User): Future[Option[Long]] = redis.get(userToRedisKey(user)).map(_.map(_.utf8String.toLong))

  private def requestLogin(identityId: Long): Future[Token] = {
    val connection = Http().outgoingConnection("localhost", 8010)
    val loginRequest = LoginRequest(identityId)
    val responseFuture = Source.single(RequestBuilding.Post("/tokens", loginRequest)).via(connection.flow).runWith(Sink.head)
    responseFuture.flatMap { response =>
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
    val connection = Http().outgoingConnection("localhost", 8010)
    val reloginRequest = ReloginRequest(tokenValue)
    val responseFuture = Source.single(RequestBuilding.Patch("/tokens", reloginRequest)).via(connection.flow).runWith(Sink.head)
    responseFuture.flatMap { response =>
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

  private def login(user: User, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    getIdentityIdFromUser(user).flatMap {
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
