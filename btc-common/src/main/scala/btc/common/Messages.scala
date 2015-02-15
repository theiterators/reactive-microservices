package btc.common

import akka.actor.ActorRef

object UserManagerMessages {
  case class LookupUser(id: Long)
}

object UserHandlerMessages {
  sealed trait Command {
    val id: Long
  }

  sealed trait Subscribe extends Command

  sealed trait ThresholdSubscribe extends Subscribe {
    val threshold: BigDecimal
  }

  case class SubscribeRateChange(override val id: Long) extends Subscribe
  case class SubscribeBidOver(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscribe
  case class SubscribeAskBelow(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscribe
  case class SubscribeVolumeOver(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscribe
  case class SubscribeVolumeBelow(override val id: Long, override val threshold: BigDecimal) extends ThresholdSubscribe

  case class Unsubscribe(override val id: Long) extends Command

  case object Heartbeat

  case object QuerySubscriptions
}

object WebSocketHandlerMessages {
  sealed trait MarketEvent

  case class OperationSuccessful(id: Long) extends MarketEvent
  case class Alarm(id: Long, value: BigDecimal) extends MarketEvent
  case class AllSubscriptions(subscriptions: Seq[UserHandlerMessages.Subscribe]) extends MarketEvent

  case class InitActorResponse(actor: ActorRef)
}
