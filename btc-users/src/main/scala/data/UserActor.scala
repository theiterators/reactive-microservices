package data

import akka.actor.{ActorLogging, ActorRef, PoisonPill, Props}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.persistence.PersistentActor
import btc.common.UserMessages._
import btc.common.WsMessages.{Alarm, AllSubscriptions}
import data.UserActor.UserId

import scala.collection.mutable
import scala.concurrent.duration._

trait UserActorConfig {
  def topic: String
  def seppukuTimeout: FiniteDuration
}

object UserActor {
  def props(id: UserId, config: UserActorConfig, WSActor: ActorRef) = Props(new UserActor(id, config, WSActor))
  case object KeepAlive

  case class ProcessData(data: DataPack)

  type UserId = Long
  type SubscriptionId = Long
  type Threshold = BigDecimal

  trait Event
  case class SubscribeEvt(id: SubscriptionId, subscription: Subscription with Evaluable) extends Event
  case class UnSubscribeEvt(id: SubscriptionId) extends Event

  sealed trait Evaluable {
    def evaluate(data: DataPack): EvaluationResult
  }

  sealed trait Thresholdable {
    val threshold: Threshold
  }

  case class EvaluationResult(active: Boolean, value: BigDecimal)

  trait RateChangeEvaluable extends Evaluable
    { override def evaluate(data: DataPack): EvaluationResult = EvaluationResult(true, data.last) }
  trait BidOverEvaluable extends Evaluable with Thresholdable
    { override def evaluate(data: DataPack): EvaluationResult = EvaluationResult(data.bid > threshold, data.bid) }
  trait AskBelowEvaluable extends Evaluable with Thresholdable
    { override def evaluate(data: DataPack): EvaluationResult = EvaluationResult(data.ask < threshold, data.ask) }
  trait VolumeOverEvaluable extends Evaluable with Thresholdable
    { override def evaluate(data: DataPack): EvaluationResult = EvaluationResult(data.volume > threshold, data.volume) }
  trait VolumeBelowEvaluable extends Evaluable with Thresholdable
    { override def evaluate(data: DataPack): EvaluationResult = EvaluationResult(data.volume < threshold, data.volume) }
}

class UserActor(id: UserId, config: UserActorConfig, WSActor: ActorRef) extends PersistentActor with ActorLogging {
  import data.UserActor._

  override def persistenceId: String = id.toString

  val subscriptions = mutable.Map.empty[SubscriptionId, Subscription with Evaluable]

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

    case QuerySubscriptions => sendAllSubscriptions()

    case pd: ProcessData => handleProcessData(pd)

    case sub: SubscribeRateChange => handleEvent(SubscribeEvt(sub.id, new SubscribeRateChange(sub.id) with RateChangeEvaluable))
    case sub: SubscribeBidOver => handleEvent(SubscribeEvt(sub.id, new SubscribeBidOver(sub.id, sub.threshold) with BidOverEvaluable))
    case sub: SubscribeAskBelow => handleEvent(SubscribeEvt(sub.id, new SubscribeAskBelow(sub.id, sub.threshold) with AskBelowEvaluable))
    case sub: SubscribeVolumeOver => handleEvent(SubscribeEvt(sub.id, new SubscribeVolumeOver(sub.id, sub.threshold) with VolumeOverEvaluable))
    case sub: SubscribeVolumeBelow => handleEvent(SubscribeEvt(sub.id, new SubscribeVolumeBelow(sub.id, sub.threshold) with VolumeBelowEvaluable))

    case Unsubscribe(ids) => handleEvent(UnSubscribeEvt(ids))
  }

  private def updateState(event: SubscribeEvt): Unit = {log.info(s"New subscription: $event"); subscriptions.put(event.id, event.subscription)}

  private def updateState(event: UnSubscribeEvt): Unit = {log.info(s"Unsubscribe: $event"); subscriptions.remove(event.id) }

  private def handleEvent(event: SubscribeEvt) = persist(event)(updateState)

  private def handleEvent(event: UnSubscribeEvt) = persist(event)(updateState)

  private def handleProcessData(pd: ProcessData) = {

    def evaluateSubscription[T <: Subscription with Evaluable](subscription: T): Unit = {
      log.info(s"Evaluating: $subscription")
      val result = subscription.evaluate(pd.data)
      if (result.active) WSActor ! Alarm(subscription.id, result.value)
    }

    subscriptions.foreach { case(ids, sub) => evaluateSubscription(sub) }
  }

  private def handleHeartBeat() = {log.info("Heartbeat!"); lastHeartBeatTime = System.currentTimeMillis()}

  private def handleKeepAlive() = {log.info("KeepAlive!"); if (System.currentTimeMillis() - lastHeartBeatTime > config.seppukuTimeout.toMillis) commitSeppuku()}

  private def commitSeppuku() = {log.info("Seppuku time!"); self ! PoisonPill}

  private def sendAllSubscriptions() = {
    log.info("Sending all subscriptions")
    WSActor ! AllSubscriptions(subscriptions.values.toSeq)
  }
}
