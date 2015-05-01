import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}

case class InternalLoginRequest(identityId: Long, authMethod: String = "codecard")
case class InternalReloginRequest(tokenValue: String, authMethod: String = "codecard")

class Gateway(implicit actorSystem: ActorSystem, materializer: FlowMaterializer, ec: ExecutionContext)
  extends JsonProtocols with Config {

  private val identityManagerConnectionFlow = Http().outgoingConnection(identityManagerHost, identityManagerPort)
  private val tokenManagerConnectionFlow = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  private def requestIdentityManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(identityManagerConnectionFlow).runWith(Sink.head)
  }

  private def requestTokenManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(tokenManagerConnectionFlow).runWith(Sink.head)
  }

  def requestToken(tokenValue: String): Future[Either[String, Token]] = {
    requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue")).flatMap { response =>
      response.status match {
        case Success(_) => Unmarshal(response.entity).to[Token].map(Right(_))
        case NotFound => Future.successful(Left("Token expired or not found"))
        case _ => Future.failed(new IOException(s"Token request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }

  def requestNewIdentity(): Future[Identity] = {
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      response.status match {
        case Success(_) => Unmarshal(response.entity).to[Identity]
        case _ => Future.failed(new IOException(s"Identity request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }

  def requestLogin(identityId: Long): Future[Token] = {
    val loginRequest = InternalLoginRequest(identityId)
    requestTokenManager(RequestBuilding.Post("/tokens", loginRequest)).flatMap { response =>
      response.status match {
        case Success(_) => Unmarshal(response.entity).to[Token]
        case _ => Future.failed(new IOException(s"Login request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }

  def requestRelogin(tokenValue: String): Future[Option[Token]] = {
    requestTokenManager(RequestBuilding.Patch("/tokens", InternalReloginRequest(tokenValue))).flatMap { response =>
      response.status match {
        case Success(_) => Unmarshal(response.entity).to[Token].map(Option(_))
        case NotFound => Future.successful(None)
        case _ => Future.failed(new IOException(s"Relogin request failed with status ${response.status} and error ${response.entity}"))
      }
    }
  }
}