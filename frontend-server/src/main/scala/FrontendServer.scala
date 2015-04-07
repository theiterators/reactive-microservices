import akka.actor.ActorSystem
import akka.http.Http
import akka.http.server.Directives._
import akka.stream.ActorFlowMaterializer
import com.typesafe.config.ConfigFactory

object FrontendServer extends App {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val staticDirectory = config.getString("static.directory")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  Http().bindAndHandle(interface = interface, port = port, handler = getFromDirectory(staticDirectory))
}
