import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
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
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val tokenManagerConnectionFlow = Http().outgoingConnection(tokenManagerHost, tokenManagerPort)

  def requestTokenManager(request: HttpRequest): Future[HttpResponse] = {
    Source.single(request).via(tokenManagerConnectionFlow).runWith(Sink.head)
  }

  Http().bindAndHandle(interface = interface, port = port, handler = {
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
  })
}
