import com.typesafe.config.Config
import java.io.IOException
import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.{ExecutionContext, Future}

case class InternalLoginRequest(identityId: Long, authMethod: String = "codeCard")
case class InternalReloginRequest(tokenValue: String, authMethod: String = "codeCard")

class Gateway(config: Config)(implicit actorSystem: ActorSystem, materializer: FlowMaterializer, ec: ExecutionContext) extends AuthCodeJsonProtocol {
  val identityManagerHost = config.getString("services.identity-manager.host")
  val identityManagerPort = config.getInt("services.identity-manager.port")
  val tokenManagerHost = config.getString("services.token-manager.host")
  val tokenManagerPort = config.getInt("services.token-manager.port")
  val identityManagerConnection = Http().outgoingConnection(identityManagerHost, identityManagerPort)
  val tokenManagerConnection = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  private def requestIdentityManager(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(identityManagerConnection.flow).runWith(Sink.head)

  private def requestTokenManager(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(tokenManagerConnection.flow).runWith(Sink.head)

  def requestToken(tokenValue: String)(implicit ec: ExecutionContext): Future[Either[String, Token]] =
    requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue")).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Token].map(Right(_))
        case NotFound => Future.successful(Left("Invalid token"))
        case status => Future.failed(new IOException(s"Token request failed with status ${response.status} and error ${response.entity.toString}")) //fixme
      }
    }

  def requestNewIdentity(implicit ec: ExecutionContext): Future[Identity] =
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Identity]
        case status => Future.failed(new IOException(s"Identity request failed with status ${response.status} and error ${response.entity.toString}")) //fixme
      }
    }


  def requestLogin(identityId: Long): Future[Token] = {
    val loginRequest = InternalLoginRequest(identityId)
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

  def requestRelogin(tokenValue: String): Future[Option[Token]] = {
    val reloginRequest = InternalReloginRequest(tokenValue)
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
}
