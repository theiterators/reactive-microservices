import akka.actor.ActorSystem
import com.restfb.types.User
import redis.RedisClient
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class Service(gateway: Gateway)(implicit actorSystem: ActorSystem, ec: ExecutionContext) extends Config {
  def register(authResponse: AuthResponse, tokenValueOption: Option[String]): Try[Future[Either[String, Identity]]] = {
    gateway.getFbUserDetails(authResponse.accessToken).map { user =>
      redis.exists(userToRedisKey(user)).flatMap {
        case true => Future.successful(Left(s"User with id ${user.getId} is already registered"))
        case false => acquireIdentity(tokenValueOption).flatMap {
          case Right(identity) => saveUserIdentityMapping(user, identity)
          case l => Future.successful(l)
        }
      }
    }
  }

  def login(authResponse: AuthResponse, tokenValueOption: Option[String]): Try[Future[Either[String, Token]]] = {
    gateway.getFbUserDetails(authResponse.accessToken).map { user =>
      getIdentityIdForUser(user).flatMap {
        case Some(identityId) => doLogin(identityId, tokenValueOption)
        case None => Future.successful(Left(s"User with id ${user.getId} is not registered"))
      }
    }
  }

  private def doLogin(identityId: Long, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    tokenValueOption match {
      case Some(tokenValue) => gateway.requestRelogin(tokenValue).map {
        case Some(token) => Right(token)
        case None => Left("Token expired or not found")
      }
      case None => gateway.requestLogin(identityId).map(Right(_))
    }
  }

  private def acquireIdentity(tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    tokenValueOption match {
      case Some(tokenValue) => gateway.requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
      case None => gateway.requestNewIdentity().map(Right(_))
    }
  }

  private def saveUserIdentityMapping(user: User, identity: Identity): Future[Either[String, Identity]] = {
    redis.setnx(userToRedisKey(user), identity.id).map {
      case true => Right(identity)
      case false => Left(s"User with id ${user.getId} is already registered")
    }
  }

  private def getIdentityIdForUser(user: User): Future[Option[Long]] = redis.get(userToRedisKey(user)).map(_.map(_.utf8String.toLong))

  private def userToRedisKey(user: User): String = s"auth-fb:id:${user.getId}"

  private val redis = RedisClient(host = redisHost, port = redisPort, password = Option(redisPassword), db = Option(redisDb))
}
