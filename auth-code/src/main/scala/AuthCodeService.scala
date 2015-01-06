import java.security.SecureRandom
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent._

class AuthCodeService(gateway: Gateway, repository: Repository)(implicit ec: ExecutionContext) extends AuthCodeConfig {
  val random = new SecureRandom

  def register(tokenValueOption: Option[String])(implicit ec: ExecutionContext): Future[Either[String, RegisterResponse]] =
    acquireIdentity(tokenValueOption).map {
      case Right(identity) =>
        val authEntry = generateAuthEntry(identity)
        val codeCard = generateCodeCard(1, authEntry.userIdentifier)
        blocking {repository.saveAuthEntryAndCodeCard(authEntry, codeCard)}
        Right(RegisterResponse(identity, codeCard))
      case Left(l) => Left(l)
    }

  def activateCode(request: ActivateCodeRequest)(implicit ec: ExecutionContext): Future[Either[String, ActivateCodeResponse]] = {
    Future.successful {
      val codes = blocking {repository.getInactiveCodesForUser(request.userIdentifier)}
      codes.length match {
        case 0 => Left("You don't have available codes")
        case _ =>
          val codeAct = codes(random.nextInt(codes.length))
          blocking { repository.activateCode(request.userIdentifier, codeAct.cardIndex, codeAct.codeIndex) }
          Right(ActivateCodeResponse(codeAct.cardIndex, codeAct.codeIndex))
      }
    }
  }

  def login(request: LoginRequest, tokenValueOption: Option[String]): Future[Either[String, Token]] = {
    blocking {repository.useCode(request.userIdentifier, request.cardIndex, request.codeIndex, request.code)} match {
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

  def getCodeCard(request: GetCodeCardRequest, tokenValueOption: Option[String]): Future[Either[String, GetCodeCardResponse]] =
    tokenValueOption match {
      case Some(tokenValue) =>
        gateway.requestRelogin(tokenValue).map {
          case None => Left("Token expired or not found")
          case Some(token) if (blocking {repository.getIdentity(request.userIdentifier)} == token.identityId) =>
            Right(GetCodeCardResponse(request.userIdentifier, generateCodeCard(repository.getNextCardIndex(request.userIdentifier), request.userIdentifier)))
          case Some(token) => Left("Token expired or not found")
        }
      case None => Future.successful(Left("Token expired or not found"))
    }

  private def acquireIdentity(tokenValueOption: Option[String])(implicit ec: ExecutionContext): Future[Either[String, Identity]] =
    tokenValueOption match {
      case Some(tokenValue) => gateway.requestToken(tokenValue).map(_.right.map(token => Identity(token.identityId)))
      case None => gateway.requestNewIdentity.map(Right(_))
    }

  private def generateAuthEntry(identity: Identity) =
    AuthEntry(f"${random.nextInt(100000)}%05d${random.nextInt(100000)}%05d", identity.id, System.currentTimeMillis(), 1)

  private def generateCodeCard(cardIndex: Long, userIdentifier: String) =
    CodeCard(cardIndex, Seq.fill(cardSize) {f"${random.nextInt(1000000)}%06d" }, userIdentifier)
}