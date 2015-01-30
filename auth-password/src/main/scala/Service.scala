import org.mindrot.jbcrypt.BCrypt
import scala.concurrent.{ExecutionContext, Future}

class Service(repository: Repository, gateway: Gateway)(implicit ec: ExecutionContext) {
  def register(request: PasswordRegisterRequest, tokenValueOption: Option[String]): Future[Either[String, Identity]] = {
    if (repository.findAuthEntry(request.email).isDefined) {
      Future.successful(Left(s"Wrong login data"))
    } else {
        acquireIdentity(tokenValueOption).map {
        case Right(identity) => Right(createEntry(request, identity))
        case l => l
      }
    }
  }

  def login(request: PasswordLoginRequest, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    repository.findAuthEntry(request.email) match {
      case None => Future.successful(Left(s"Wrong login data"))
      case Some(entry) =>
        if (!checkPassword(request.password, entry.password)) {
          Future.successful(Left(s"Wrong login data"))
        } else {
          tokenValueOption match {
            case None => gateway.requestLogin(entry.identityId).map(Right(_))
            case Some(tokenValue) =>
              gateway.requestRelogin(tokenValue).map {
                case Some(token) => Right(token)
                case None => Left("Token expired or not found")
              }
          }
        }
    }
  }

  def reset(request: PasswordResetRequest, tokenValue: String): Future[Either[String, Identity]] = {
    repository.findAuthEntry(request.email) match {
      case None => Future.successful(Left(s"Wrong login data"))
      case Some(entry) =>
        gateway.requestToken(tokenValue).flatMap {
          case Right(token) =>
            if (entry.identityId != token.identityId) {
              Future.successful(Left(s"Wrong login data"))
            }
            else {
              val passHash = hashPassword(request.newPassword)
              repository.updateAuthEntry(entry.copy(password = passHash))
              Future.successful(Right(Identity(entry.identityId)))
            }
          case Left(s) => Future.successful(Left(s))
        }
    }
  }

  private def createEntry(request: PasswordRegisterRequest, identity: Identity): Identity = {
    val passHash = hashPassword(request.password)
    val entry = AuthEntry(None, identity.id, System.currentTimeMillis, request.email, passHash)
    repository.createAuthEntry(entry)
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
