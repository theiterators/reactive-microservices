import akka.actor.ActorSystem
import akka.event.Logging
import java.math.BigInteger
import java.security.SecureRandom
import scala.concurrent.{ExecutionContext, Future}

class Service(repository: Repository)(implicit actorSystem: ActorSystem, ec: ExecutionContext) extends Config {
  def relogin(reloginRequest: ReloginRequest): Future[Option[Token]] = {
    repository.addMethodToValidTokenByValue(reloginRequest.tokenValue, reloginRequest.authMethod)
  }

  def login(loginRequest: LoginRequest): Future[Token] = {
    val newToken = createFreshToken(loginRequest.identityId, loginRequest.authMethod)
    repository.insertToken(newToken).map(_ => newToken)
  }

  def findAndRefreshToken(tokenValue: String): Future[Option[Token]] = {
    repository.findValidTokenByValue(tokenValue).map { tokenOption =>
      tokenOption.map { token =>
        val newToken = refreshToken(token)
        if (newToken != token)
          repository.updateTokenByValue(token.value, newToken).onFailure { case t => logger.error(t, "Token refreshment failed") }
        newToken
      }
    }
  }

  def logout(tokenValue: String): Unit = {
    repository.deleteTokenByValue(tokenValue).onFailure { case t => logger.error(t, "Token deletion failed") }
  }

  private def createFreshToken(identityId: Long, authMethod: String): Token = {
    Token(generateToken, System.currentTimeMillis() + tokenTtl, identityId, Set(authMethod))
  }

  private def generateToken: String = new BigInteger(255, random).toString(32)

  private def refreshToken(token: Token): Token = token.copy(validTo = math.max(token.validTo, System.currentTimeMillis() + sessionTtl))

  private val random = new SecureRandom()

  private val logger = Logging(actorSystem, getClass)
}
