import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer
import java.math.BigInteger
import java.security.SecureRandom
import com.typesafe.config.ConfigFactory
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.{BSONDocument, Macros}
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.Future
import spray.json.DefaultJsonProtocol

case class LoginRequest(identityId: Long, authMethod: String)

case class ReloginRequest(tokenValue: String, authMethod: String)

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

trait TokenManagerMongoHandlers {
  protected implicit val tokenHandler = Macros.handler[Token]
}

trait TokenManagerJsonProtocols extends DefaultJsonProtocol {
  protected implicit val tokenFormat = jsonFormat4(Token.apply)
  protected implicit val reloginRequestFormat = jsonFormat2(ReloginRequest.apply)
  protected implicit val loginRequestFormat = jsonFormat2(LoginRequest.apply)
}

object TokenManager extends App with TokenManagerMongoHandlers with TokenManagerJsonProtocols {
  private val config = ConfigFactory.load()
  private val interface = config.getString("http.interface")
  private val port = config.getInt("http.port")
  private val mongoHost = config.getString("mongo.host")
  private val mongoDb = config.getString("mongo.db")
  private val mongoUser = config.getString("mongo.user")
  private val mongoPassword = config.getString("mongo.password")
  private val sessionLength = config.getLong("session.length") * 1000 // milliseconds
  private val tokenValidityLength = config.getLong("token.validityLength") * 1000 // milliseconds

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = FlowMaterializer()
  private implicit val dispatcher = actorSystem.dispatcher
  private val logger = Logging(actorSystem, getClass)

  private val mongoConnection = (new MongoDriver).connection(nodes = List(mongoHost), authentications = List(Authenticate(mongoDb, mongoUser, mongoPassword)))
  private val mongoDatabase = mongoConnection(mongoDb)
  private val tokens: BSONCollection = mongoDatabase("tokens")

  private val random = new SecureRandom()

  private def generateToken: String = {
    new BigInteger(255, random).toString(32)
  }

  private def insertToken(token: Token): Future[Token] = {
    tokens.insert(token).map(_ => token)
  }

  private def updateTokenByValue(value: String, token: Token): Future[Int] = tokens.update(BSONDocument("value" -> value), token).map(_.updated)

  private def deleteTokenByValue(value: String): Future[Int] = {
    tokens.remove(BSONDocument("value" -> value)).map(_.updated)
  }

  private def findValidTokenByValue(value: String): Future[Option[Token]] = {
    tokens.find(BSONDocument("value" -> value, "validTo" -> BSONDocument("$gt" -> System.currentTimeMillis()))).cursor[Token].headOption
  }

  private def addMethodToValidTokenByValue(value: String, method: String): Future[Option[Token]] = {
    tokens.update(BSONDocument("value" -> value), BSONDocument("$push" -> BSONDocument("authMethods" -> method))).flatMap { lastError =>
      if (lastError.updated > 0) findValidTokenByValue(value) else Future.successful(None)
    }
  }

  private def createFreshToken(identityId: Long, authMethod: String): Token = {
    Token(generateToken, System.currentTimeMillis() + tokenValidityLength, identityId, Set(authMethod))
  }

  private def refreshToken(token: Token): Token = token.copy(validTo = math.max(token.validTo, System.currentTimeMillis() + sessionLength))

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("token-manager") {
      pathPrefix("tokens") {
        pathEndOrSingleSlash {
          post {
            entity(as[LoginRequest]) { loginRequest =>
              complete {
                val newToken = createFreshToken(loginRequest.identityId, loginRequest.authMethod)
                insertToken(newToken).map(_ => newToken)
              }
            }
          } ~
          patch {
            entity(as[ReloginRequest]) { reloginRequest =>
              complete {
                addMethodToValidTokenByValue(reloginRequest.tokenValue, reloginRequest.authMethod).map {
                  case Some(token) => ToResponseMarshallable(OK -> token)
                  case None => ToResponseMarshallable(NotFound -> "Token expired or not found")
                }
              }
            }
          }
        } ~
        path(Segment) { tokenValue =>
          pathEndOrSingleSlash {
            get {
              complete {
                findValidTokenByValue(tokenValue).map {
                  case Some(token) =>
                    val newToken = refreshToken(token)
                    if (newToken != token) updateTokenByValue(token.value, newToken).onFailure { case t => logger.error(t, "Token refreshment failed") }

                    ToResponseMarshallable(token)
                  case None => ToResponseMarshallable(NotFound -> "Token expired or not found")
                }
              }
            } ~
            delete {
              complete {
                deleteTokenByValue(tokenValue).onFailure { case t => logger.error(t, "Token deletion failed") }
                OK
              }
            }
          }
        }
      }
    }
  }
}
