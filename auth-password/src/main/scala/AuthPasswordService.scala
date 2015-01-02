import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration._

class AuthPasswordService(gateway: Gateway) {

  def register(request: PasswordRegisterRequest, tokenValueOption: Option[String]): Either[String, Identity] = {

    if (blocking { Repository.exists(request.email) }) {
      Left(s"User with email ${request.email.address} is already registered")
    } else {
      val identityEitherFuture: Future[Either[String, Identity]] = acquireIdentity(tokenValueOption)

      Await.result(identityEitherFuture, 5000 millis) match {
        case Right(identity) => Right(createEntry(request, identity))
        case l => l
      }
    }
  }

  def login(request: PasswordLoginRequest, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    Repository.get(request.email) match {
      case None => Future.successful(Left(s"User with email ${request.email.address} is not registered"))
      case Some(entry) => {
        if (!checkPassword(request.password, entry.password)) {
          Future.successful(Left(s"Wrong password for email ${request.email.address}"))
        } else {
          tokenValueOption match {
            case None => gateway.requestLogin(entry.identityId).map(Right(_))
            case Some(tokenValue) => {
              gateway.requestRelogin(tokenValue).map {
                case Some(token) => Right(token)
                case None => Left("Token expired or not found")
              }
            }
          }
        }
      }
    }
  }

  def reset(request: PasswordResetRequest) = ???

  private def createEntry(request: PasswordRegisterRequest, identity: Identity): Identity = {
    val passHash = hashPassword(request.password)
    val entry = AuthEntry(None, identity.id, System.currentTimeMillis, request.email, passHash)
    blocking { Repository.save(entry) }
    identity
  }

  private def acquireIdentity(tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    tokenValueOption match {
      case Some(tokenValue) => gateway.requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
      case None => gateway.requestNewIdentity().map(Right(_))
    }
  }

  private def hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))

  private def checkPassword(password: String, passwordHash: String): Boolean = BCrypt.checkpw(password, passwordHash)
}
