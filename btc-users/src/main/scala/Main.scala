import akka.actor._
import akka.routing.{NoRoutee, AddRoutee, BroadcastGroup}
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

object Main extends App {
  val config = ConfigFactory.load()

  val applicationName = config.getString("application.name")
  val dataFetcherInterval = config.getLong("data-fetcher.interval").millis
  val keepAliveTimeout = config.getLong("user-handler.timeout").millis

  implicit val system = ActorSystem(applicationName, config)

  val broadcaster = system.actorOf(BroadcastGroup(List()).props())
  broadcaster ! AddRoutee(NoRoutee)

  val dataFetcher = system.actorOf(DataFetcher.props(broadcaster))
  system.scheduler.schedule(dataFetcherInterval, dataFetcherInterval, dataFetcher, DataFetcher.Tick)(system.dispatcher)

  val manager = system.actorOf(UsersManager.props(broadcaster, keepAliveTimeout), "users-manager")
}
