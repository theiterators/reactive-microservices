import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.persistence.PersistentActor
import akka.routing.{RemoveRoutee, ActorRefRoutee, AddRoutee}
import btc.common.UserHandlerMessages._
import btc.common.WebSocketHandlerMessages.{OperationSuccessful, Alarm, AllSubscriptions}
import scala.collection.mutable
import scala.concurrent.duration._
import UserHandler._

object UserHandler {
  case object KeepAlive

  case class Ticker(max: BigDecimal, min: BigDecimal, last: BigDecimal, bid: BigDecimal, ask: BigDecimal, vwap: BigDecimal, average: BigDecimal, volume: BigDecimal)

  def props(userId: Long, wsActor: ActorRef, broadcaster: ActorRef, keepAliveTimeout: FiniteDuration) = {
    Props(new UserHandler(userId, wsActor, broadcaster, keepAliveTimeout))
  }
}

class UserHandler(userId: Long, wsActor: ActorRef, broadcaster: ActorRef, keepAliveTimeout: FiniteDuration) extends PersistentActor with ActorLogging {
  override val persistenceId: String = userId.toString

  override def preStart(): Unit = {
    super.preStart()
    broadcaster ! AddRoutee(ActorRefRoutee(self))
  }

  override def postStop(): Unit = {
    super.postStop()
    broadcaster ! RemoveRoutee(ActorRefRoutee(self))
  }

  override def receiveRecover: Receive = {
    case subscribe: Subscribe => updateState(subscribe)
    case unsubscribe: Unsubscribe => updateState(unsubscribe)
  }

  override def receiveCommand: Receive = {
    case KeepAlive if System.currentTimeMillis() - lastHeartBeatTime > keepAliveTimeout.toMillis =>
      log.info(s"Timeout while waiting for heartbeat for user $userId, stopping")
      self ! PoisonPill
    case Heartbeat =>
      log.debug(s"Got heartbeat for user $userId")
      lastHeartBeatTime = System.currentTimeMillis()
      sender() ! Heartbeat
    case QuerySubscriptions =>
      log.info(s"Got request for subscriptions for user $userId")
      wsActor ! AllSubscriptions(subscriptions.values.toList)
    case ticker: Ticker =>
      val alarms = getAlarmsForTicker(ticker)
      log.debug(s"Got ticker and sending alarms $alarms for user $userId")
      alarms.foreach(wsActor ! _)
    case subscribe: Subscribe =>
      log.debug(s"Got subscribe request $subscribe for user $userId")
      persist(subscribe) { e =>
        updateState(e)
        wsActor ! OperationSuccessful(e.id)
      }
    case unsubscribe: Unsubscribe =>
      log.debug(s"Got unsubscribe request $unsubscribe for user $userId")
      persist(unsubscribe) { e =>
        updateState(e)
        wsActor ! OperationSuccessful(e.id)
      }
  }

  private def updateState(subscribe: Subscribe) = subscriptions.put(subscribe.id, subscribe)

  private def updateState(unsubscribe: Unsubscribe) = subscriptions.remove(unsubscribe.id)

  private def getAlarmsForTicker(ticker: Ticker): List[Alarm] = {
    subscriptions.values.map {
      case SubscribeRateChange(id) => Option(Alarm(id, ticker.average))
      case SubscribeBidOver(id, threshold) => if (ticker.bid > threshold) Option(Alarm(id, ticker.bid)) else None
      case SubscribeAskBelow(id, threshold) => if (ticker.ask < threshold) Option(Alarm(id, ticker.ask)) else None
      case SubscribeVolumeOver(id, threshold) => if (ticker.volume > threshold) Option(Alarm(id, ticker.volume)) else None
      case SubscribeVolumeBelow(id, threshold) => if (ticker.volume < threshold) Option(Alarm(id, ticker.volume)) else None
    }.toList.flatten
  }

  private val subscriptions = mutable.Map.empty[Long, Subscribe]
  private var lastHeartBeatTime = System.currentTimeMillis()
}