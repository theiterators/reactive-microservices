import akka.actor.{PoisonPill, ActorSystem, Props}
import akka.contrib.pattern.{ClusterReceptionistExtension, ClusterSingletonManager}
import com.typesafe.config.ConfigFactory
import data._
import scala.concurrent.duration._

object Main extends App {
  val config = ConfigFactory.load()

  implicit val system = ActorSystem(config.getString("application.cluster.name"), config)

  val configDB = new DataBroadcasterConfig {
    override val topic: String = config.getString("application.cluster.topic")
  }

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = DataBroadcaster.props(configDB),
    singletonName = "broadcaster",
    terminationMessage = PoisonPill,
    role = None),
    name = "singleton")

  val configUA = new UserActorConfig {
    override val seppukuTimeout: FiniteDuration = FiniteDuration(10000, MILLISECONDS)

    override val topic: String = config.getString("application.cluster.topic")
  }

  val configUM = new UsersManagerConfig {
    override val userConfig: UserActorConfig = configUA
  }

  val manager = system.actorOf(UsersManager.props(configUM), "users-manager")

  ClusterReceptionistExtension(system).registerService(manager)
}
