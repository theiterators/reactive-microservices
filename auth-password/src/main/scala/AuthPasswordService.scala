import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.{ExecutionContext, Future, blocking}

class AuthPasswordService(gateway: Gateway)(implicit ec: ExecutionContext) {

  def register(request: PasswordRegisterRequest, tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    if (blocking(Repository.exists(request.email))) {
      Future.successful(Left(s"Wrong login data"))
    } else {
        acquireIdentity(tokenValueOption).map {
        case Right(identity) => Right(createEntry(request, identity))
        case l => l
      }
    }
  }

  def login(request: PasswordLoginRequest, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    blocking(Repository.get(request.email)) match {
      case None => Future.successful(Left(s"Wrong login data"))
      case Some(entry) => {
        if (!checkPassword(request.password, entry.password)) {
          Future.successful(Left(s"Wrong login data"))
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
    blocking(Repository.save(entry))
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
