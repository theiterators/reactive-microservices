package controllers

import akka.actor.{Props, Actor, ActorRef}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

trait Formats {
  implicit val subscribeThresholdWritesFormat = new Writes[SubscribeThreshold] {
    override def writes(o: SubscribeThreshold): JsValue = Json.obj(
      "id" -> o.id,
      "threshold" -> o.threshold,
      "operation" -> o.operation
    )
  }

  implicit val subscribeChangeWritesFormat = new Writes[SubscribeChange] {
    override def writes(o: SubscribeChange): JsValue = Json.obj(
      "id" -> o.id,
      "operation" -> o.operation
    )
  }

  implicit val subscribeWritesFormat = new Writes[Subscribe] {
    override def writes(o: Subscribe): JsValue = o match {
      case o: SubscribeChange => subscribeChangeWritesFormat.writes(o)
      case o: SubscribeThreshold => subscribeThresholdWritesFormat.writes(o)
    }
  }

  implicit val userEventFormat = new Format[UserEvent] {
    override def writes(o: UserEvent): JsValue = ??? // not needed because it's in type

    override def reads(json: JsValue): JsResult[UserEvent] = {
      val idOption = (json \ "id").asOpt[Long]
      val operationOption = (json \ "operation").asOpt[String]
      val thresholdOption = (json \ "threshold").asOpt[BigDecimal]
      (idOption, operationOption, thresholdOption) match {
        case (Some(id), Some(operation), Some(threshold)) =>
          operation match {
            case SubscribeBidOver.Operation => JsSuccess(SubscribeBidOver(id, threshold))
            case SubscribeAskBelow.Operation => JsSuccess(SubscribeAskBelow(id, threshold))
            case SubscribeVolumeOver.Operation => JsSuccess(SubscribeVolumeOver(id, threshold))
            case SubscribeVolumeBelow.Operation => JsSuccess(SubscribeVolumeBelow(id, threshold))
            case _ => JsError("Unknown operation")
          }
        case (Some(id), Some(operation), None) =>
          operation match {
            case SubscribeRateChange.Operation => JsSuccess(SubscribeRateChange(id))
            case Unsubscribe.Operation => JsSuccess(Unsubscribe(id))
            case _ => JsError("Unknown operation or missing 'threshold' field")
          }
        case _ => JsError("Missing fields 'id' and/or 'operation'")
      }
    }
  }

  implicit val operationSuccessfulWritesFormat = Json.writes[OperationSuccessful]

  implicit val alarmWritesFormat = Json.writes[Alarm]

  implicit val allSubscriptionsWritesFormat = Json.writes[AllSubscriptions]

  implicit val marketEventFormat = new Format[MarketEvent] {
    override def writes(o: MarketEvent): JsValue = o match {
      case o: OperationSuccessful => operationSuccessfulWritesFormat.writes(o)
      case o: Alarm => alarmWritesFormat.writes(o)
      case o: AllSubscriptions => allSubscriptionsWritesFormat.writes(o)
    }

    override def reads(json: JsValue): JsResult[MarketEvent] = ??? // not needed because it's out type
  }

  implicit val marketEventFrameFormatter = FrameFormatter.jsonFrame[MarketEvent]

  implicit val userEventFrameFormatter = FrameFormatter.jsonFrame[UserEvent]

  implicit val tokenReadsFormat = Json.reads[Token]
}

sealed trait UserEvent {
  val id: Long
  val operation: String
}

sealed trait Subscribe extends UserEvent

sealed trait SubscribeChange extends Subscribe

sealed trait SubscribeThreshold extends Subscribe {
  val threshold: BigDecimal
}

case class SubscribeRateChange(override val id: Long) extends SubscribeChange { override val operation: String = SubscribeRateChange.Operation }
object SubscribeRateChange { val Operation = "SubscribeRateChange" }

case class SubscribeBidOver(override val id: Long, override val threshold : BigDecimal) extends SubscribeThreshold { override val operation: String = SubscribeBidOver.Operation }
object SubscribeBidOver { val Operation = "SubscribeBidOver" }

case class SubscribeAskBelow(override val id: Long, override val threshold : BigDecimal) extends SubscribeThreshold { override val operation: String = SubscribeAskBelow.Operation }
object SubscribeAskBelow { val Operation = "SubscribeAskBelow" }

case class SubscribeVolumeOver(override val id: Long, override val threshold: BigDecimal) extends SubscribeThreshold { override val operation: String = SubscribeVolumeOver.Operation }
object SubscribeVolumeOver { val Operation = "SubscribeVolumeOver" }

case class SubscribeVolumeBelow(override val id: Long, override val threshold: BigDecimal) extends SubscribeThreshold { override val operation: String = SubscribeVolumeBelow.Operation }
object SubscribeVolumeBelow { val Operation = "SubscribeVolumeBelow" }

case class Unsubscribe(override val id: Long) extends UserEvent { override val operation: String = Unsubscribe.Operation }
object Unsubscribe { val Operation = "Unsubscribe" }

sealed trait MarketEvent

case class OperationSuccessful(id: Long) extends MarketEvent

case class Alarm(id: Long, value: BigDecimal) extends MarketEvent

case class AllSubscriptions(subscriptions: Seq[Subscribe]) extends MarketEvent

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object BtcWs extends Controller with Formats {
  def index(authToken: String) = WebSocket.tryAcceptWithActor[UserEvent, MarketEvent] { implicit request =>
    tokenManagerUrl(authToken).get().map { response =>
      if (response.status == OK) {
        val token = Json.parse(response.body).as[Token]
        Right(WebSocketHandlerActor.props(token, _: ActorRef))
      } else {
        Left(Unauthorized("Token expired or not found"))
      }
    }
  }

  private def tokenManagerUrl(authToken: String) = WS.url(s"http://$tokenManagerHost:$tokenManagerPort/tokens/$authToken")
  private val tokenManagerHost = current.configuration.getString("services.token-manager.host").get
  private val tokenManagerPort = current.configuration.getInt("services.token-manager.port").get
}

object WebSocketHandlerActor {
def props(token: Token, out: ActorRef) = Props(new WebSocketHandlerActor(token, out))
}

class WebSocketHandlerActor(token: Token, out: ActorRef) extends Actor {
  override def receive: Receive = {
    case r: UserEvent =>
      out ! OperationSuccessful(r.id)
  }
}
