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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Gateway(implicit actorSystem: ActorSystem, materializer: FlowMaterializer)
  extends AuthPasswordJsonProtocols with AuthPasswordConfig {

  private val identityManagerConnection = Http().outgoingConnection(identityManagerHost, identityManagerPort)
  private val tokenManagerConnection = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  private def requestIdentityManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(identityManagerConnection.flow).runWith(Sink.head)
  }

  private def requestTokenManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(tokenManagerConnection.flow).runWith(Sink.head)
  }

  def requestToken(tokenValue: String): Future[Either[String, Token]] = {
    requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue")).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Token].map(Right(_))
      } else if (response.status == NotFound) {
        Future.successful(Left("Token expired or not found"))
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Token request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  def requestNewIdentity(): Future[Identity] = {
    requestIdentityManager(RequestBuilding.Post("/identities")).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[Identity]
      } else {
        Unmarshal(response.entity).to[String].flatMap { errorMessage =>
          Future.failed(new IOException(s"Identity request failed with status ${response.status} and error $errorMessage"))
        }
      }
    }
  }

  def requestLogin(identityId: Long): Future[Token] = {
    val loginRequest = LoginRequest(identityId)
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
    val reloginRequest = ReloginRequest(tokenValue)
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
