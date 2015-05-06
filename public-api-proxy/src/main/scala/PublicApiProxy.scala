
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import scala.util.{Failure => FailureT, Success => SuccessT}

object PublicApiProxy extends App {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val staticDirectory = config.getString("static.directory")
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  Http().bindAndHandle(interface = interface, port = port, handler = {
    logRequestResult("auth-fb") { complete(OK) }
  })