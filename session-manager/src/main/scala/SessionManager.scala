import akka.actor.ActorSystem
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.server.Directives._
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future

object SessionManager extends App {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val tokenManagerHost = config.getString("services.token-manager.host")
  val tokenManagerPort = config.getInt("services.token-manager.port")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = FlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val tokenManagerConnectionFlow = Http().outgoingConnection(tokenManagerHost, tokenManagerPort).flow

  def requestTokenManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(tokenManagerConnectionFlow).runWith(Sink.head)
  }

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("session-manager") {
      path("session") {
        headerValueByName("Auth-Token") { tokenValue =>
          pathEndOrSingleSlash {
            get {
              complete {
                requestTokenManager(RequestBuilding.Get(s"/tokens/$tokenValue"))
              }
            } ~
            delete {
              complete {
                requestTokenManager(RequestBuilding.Delete(s"/tokens/$tokenValue"))
              }
            }
          }
        }
      }
    }
  }
}
