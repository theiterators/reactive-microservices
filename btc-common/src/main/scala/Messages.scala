import akka.actor.ActorRef
import domain._

object UserManagerMessages {
  case class LookupUser(id: UserId)
}

object UserMessages {
  case object HeartBeat

  sealed trait Command extends Serializable
  sealed trait Subscription extends Command

  case class SubscribeRateChange(id: SubscriptionId) extends Subscription
  case class SubscribeBidOver(id: SubscriptionId, threshold: Threshold) extends Subscription
  case class SubscribeAskBelow(id: SubscriptionId, threshold: Threshold) extends Subscription
  case class SubscribeVolumeOver(id: SubscriptionId, threshold: Threshold) extends Subscription
  case class SubscribeVolumeBelow(id: SubscriptionId, threshold: Threshold) extends Subscription

  case class Unsubscribe(id: SubscriptionId) extends Command
}

object WSActorMessages {
  sealed trait MarketEvent
  case class OperationSuccessful(id: SubscriptionId) extends MarketEvent
  case class Alarm(id: SubscriptionId, value: BigDecimal) extends MarketEvent
  case class AllSubscriptions(subscriptions: Seq[UserMessages.Subscription]) extends MarketEvent

  case class InitActorResponse(actor: ActorRef)
}
