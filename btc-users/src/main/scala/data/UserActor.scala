package data

import akka.actor.{PoisonPill, ActorRef, ActorLogging, Props}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.io.Tcp.SO.KeepAlive
import akka.persistence.PersistentActor
import data.UserActor.UserId
import data.UsersManager.UserId
import domain.{SubscriptionId, Threshold}

import scala.collection.mutable
import scala.concurrent.duration._

trait UserActorConfig {
  def topic: String
  def seppukuTimeout: FiniteDuration
}

object UserActor {
  def props(id: UserId, config: UserActorConfig, WSActor: ActorRef) = Props(new UserActor(id, config, WSActor)
  case object KeepAlive

  case class ProcessData(data: DataPack) extends Command

  trait Event
  case class SubscribeEvt(id: SubscriptionId, subscription: Subscription) extends Event
  case class UnSubscribeEvt(id: SubscriptionId) extends Event

  abstract class Subscription(threshold : Threshold) extends Serializable { def evaluate(data: DataPack): Boolean }
  case class BidOverSubscription(threshold : Threshold) extends Subscription(threshold)
    { override def evaluate(data: DataPack): Boolean =  data.bid > threshold }

  case class AskBelowSubscription(threshold : Threshold) extends Subscription(threshold)
    { override def evaluate(data: DataPack): Boolean = data.ask < threshold }

  case class VolumeOverSubscription(threshold : Threshold) extends Subscription(threshold)
    { override def evaluate(data: DataPack): Boolean = data.volume > threshold }

  case class VolumeBelowSubscription(threshold : Threshold) extends Subscription(threshold)
    { override def evaluate(data: DataPack): Boolean = data.volume < threshold }

}

class UserActor(id: UserId, config: UserActorConfig, WSActor: ActorRef) extends PersistentActor with ActorLogging {
  import data.UserActor._

  override def persistenceId: String = id.toString

  val subscriptions = mutable.Map.empty[SubscriptionId, Subscription]

  private val topic = config.topic

  private var lastHeartBeatTime: Long = System.currentTimeMillis()

  private val mediator = DistributedPubSubExtension(context.system).mediator
  mediator ! Subscribe(topic, self)

  implicit val ec = context.dispatcher
  context.system.scheduler.schedule(5 seconds, config.seppukuTimeout, self, KeepAlive)

  override def receive = {
    case SubscribeAck(Subscribe(`topic`, _, `self`)) => context become receiveCommand
  }

  override def receiveRecover: Receive = {
    case evt: SubscribeEvt => updateState(evt)
    case evt: UnSubscribeEvt => updateState(evt)
  }

  override val receiveCommand: Receive = {
    case KeepAlive => handleKeepAlive()

    case HeartBeat => handleHeartBeat()

    case pd: ProcessData => handleProcessData(pd)

    case SubscribeBidOver(ids, thr) => handleEvent(SubscribeEvt(ids, BidOverSubscription(thr)))
    case SubscribeAskBelow(ids, thr) => handleEvent(SubscribeEvt(ids, AskBelowSubscription(thr)))
    case SubscribeVolumeOver(ids, thr) => handleEvent(SubscribeEvt(ids, VolumeOverSubscription(thr)))
    case SubscribeVolumeBelow(ids, thr) => handleEvent(SubscribeEvt(ids, VolumeBelowSubscription(thr)))

    case UnSubscribe(ids) => handleEvent(UnSubscribeEvt(ids))
  }

  private def updateState(event: SubscribeEvt): Unit = {log.info(s"New subscription: $event"); subscriptions.put(event.id, event.subscription)}

  private def updateState(event: UnSubscribeEvt): Unit = {log.info(s"Unsubscribe: $event"); subscriptions.remove(event.id) }

  private def handleEvent(event: SubscribeEvt) = persist(event)(updateState)

  private def handleEvent(event: UnSubscribeEvt) = persist(event)(updateState)

  private def handleProcessData(pd: ProcessData) = {
    //log.info(s"New data in Subscriber ${pd.data}")
    subscriptions.foreach { case(ids, sub) =>
      log.info(s"Evaluating: $ids, $sub")
      if (sub.evaluate(pd.data)) WSActor ! Alarm(ids)
    }
  }

  private def handleHeartBeat() = {log.info("Heartbeat!"); lastHeartBeatTime = System.currentTimeMillis()}

  private def handleKeepAlive() = {log.info("KeepAlive!"); if (System.currentTimeMillis() - lastHeartBeatTime > config.seppukuTimeout.toMillis) commitSeppuku()}

  private def commitSeppuku() = {log.info("Seppuku time!"); self ! PoisonPill}
}
