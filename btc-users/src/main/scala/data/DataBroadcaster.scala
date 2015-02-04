package data

import akka.actor.{Actor, ActorLogging, Props}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.stream.FlowMaterializer
import com.typesafe.config.ConfigFactory
import data.DataBroadcaster.Tick
import data.UserActor.ProcessData

import scala.concurrent.duration._

trait DataBroadcasterConfig {
  def topic: String
}

object DataBroadcaster {
  def props(config: DataBroadcasterConfig) = Props(new DataBroadcaster(config))

  sealed trait DataBroadcasterMessages
  case object Tick extends DataBroadcasterMessages
}

class DataBroadcaster(config: DataBroadcasterConfig) extends Actor with ActorLogging {

  implicit val system = context.system
  import context.dispatcher
  implicit val materializer = FlowMaterializer()

  private val service = new DataImportService
  private val topic = config.topic
  private val mediator = DistributedPubSubExtension(system).mediator

  system.scheduler.schedule(0 milliseconds, 1 second, self, Tick) //take numbers from config

  def receive = {
    case Tick => handleTickMessage()
  }

  private def handleTickMessage() = {
    val data = service.importData

    data.onSuccess {
      case dp =>
        //log.info(s"New data: $dp")
        mediator ! Publish(topic, ProcessData(dp))
    }

    data.onFailure {
      case t => log.error("Could not fetch data!", t)
    }
  }
}


