import java.io.IOException
import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.StatusCodes._
import akka.http.model.{HttpRequest, HttpResponse}
import akka.http.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.Config
import scala.concurrent.{ExecutionContext, Future}

case class InternalLoginRequest(identityId: Long, authMethod: String = "codecard")

case class InternalReloginRequest(tokenValue: String, authMethod: String = "codecard")

class Gateway(implicit actorSystem: ActorSystem, materializer: FlowMaterializer, ec: ExecutionContext) extends AuthCodeJsonProtocol with AuthCodeConfig{
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
        case status => Future.failed(new IOException(s"Token request failed with status ${response.status} and error ${response.entity}"))
      }
    }

  def requestNewIdentity(implicit ec: ExecutionContext): Future[Identity] =
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Identity]
        case status => Future.failed(new IOException(s"Identity request failed with status ${response.status} and error ${response.entity}"))
      }
    }

  def requestLogin(identityId: Long): Future[Token] = {
    val loginRequest = InternalLoginRequest(identityId)
    requestTokenManager(RequestBuilding.Post("/tokens", loginRequest)).flatMap { response =>
      response.status match {
        case status if status.isSuccess => Unmarshal(response.entity).to[Token]
        case status => Future.failed(new IOException(s"Login request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }

  def requestRelogin(tokenValue: String): Future[Option[Token]] = {
    val reloginRequest = InternalReloginRequest(tokenValue)
    requestTokenManager(RequestBuilding.Patch("/tokens", reloginRequest)).flatMap { response =>
      response.status match {
        case NotFound => Future.successful(None)
        case status if status.isSuccess => Unmarshal(response.entity).to[Token].map(Option(_))
        case status => Future.failed(new IOException(s"Relogin request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }
}