import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.blocking

class AuthPasswordService(gateway: Gateway) {

  def register(request: PasswordRegisterRequest, tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    blocking {
      if (Repository.exists(request.email)) {
        Future.successful(Left(s"User with email ${request.email.address} is already registered"))
      } else {
        val identityEitherFuture: Future[Either[String, Identity]] = tokenValueOption match {
          case Some(tokenValue) => gateway.requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
          case None => gateway.requestNewIdentity().map(Right(_))
        }

        identityEitherFuture.flatMap {
          case Right(identity) => {
            val passHash = hashPassword(request.password)
            val entry = AuthEntry(None, identity.id, System.currentTimeMillis, request.email, passHash)
            Repository.save(entry)
            Future.successful(Right(identity))
          }
          case l => Future.successful(l)
        }
      }
    }
  }

  def reset(request: PasswordResetRequest) = ???

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

  private def hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))

  private def checkPassword(password: String, passwordHash: String): Boolean = BCrypt.checkpw(password, passwordHash)
}
