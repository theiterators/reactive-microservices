import akka.actor._
import btc.common.UserManagerMessages.LookupUser
import btc.common.WebSocketHandlerMessages.InitActorResponse
import scala.concurrent.duration.FiniteDuration

object UsersManager {
  def props(broadcaster: ActorRef, keepAliveTimeout: FiniteDuration) = Props(new UsersManager(broadcaster, keepAliveTimeout))
}

class UsersManager(broadcaster: ActorRef, keepAliveTimeout: FiniteDuration) extends Actor with ActorLogging {
  override def receive: Receive = {
    case LookupUser(id) =>
      log.info(s"Got user lookup request with id $id")
      val userHandler = context.actorOf(UserHandler.props(id, sender(), broadcaster, keepAliveTimeout))
      context.system.scheduler.schedule(keepAliveTimeout * 3, keepAliveTimeout, userHandler, UserHandler.KeepAlive)(context.system.dispatcher)
      sender() ! InitActorResponse(userHandler)
  }
}