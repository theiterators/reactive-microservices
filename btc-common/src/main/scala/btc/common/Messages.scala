package btc.common

import akka.actor.ActorRef

object UserManagerMessages {
  case class LookupUser(id: Long)
}

object UserMessages {
  case object HeartBeat

  sealed trait Command extends Serializable {
    val id: Long
  }

  sealed trait Subscription extends Command

  sealed trait ThresholdSubscription extends Subscription {
    val threshold: BigDecimal
  }

  case class SubscribeRateChange(override val id: Long) extends Subscription
  case class SubscribeBidOver(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscription
  case class SubscribeAskBelow(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscription
  case class SubscribeVolumeOver(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscription
  case class SubscribeVolumeBelow(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscription

  case class Unsubscribe(override val id: Long) extends Command
}

object WsMessages {
  sealed trait MarketEvent
  case class OperationSuccessful(id: Long) extends MarketEvent
  case class Alarm(id: Long, value: BigDecimal) extends MarketEvent
  case class AllSubscriptions(subscriptions: Seq[UserMessages.Subscription]) extends MarketEvent

  case class InitActorResponse(actor: ActorRef)
}
