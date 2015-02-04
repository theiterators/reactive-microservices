package controllers

import akka.actor.{Props, Actor, ActorRef}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import btc.common.UserMessages._
import btc.common.WsMessages._

trait Formats {
  implicit val userEventFormat = new Format[Command] {
    override def writes(o: Command): JsValue = o match {
      case SubscribeRateChange(id) => Json.obj("operation" -> "SubscribeRateChange", "id" -> id)
      case SubscribeBidOver(id, threshold) => Json.obj("operation" -> "SubscribeBidOver", "id" -> id, "threshold" -> threshold)
      case SubscribeAskBelow(id, threshold) => Json.obj("operation" -> "SubscribeAskBelow", "id" -> id, "threshold" -> threshold)
      case SubscribeVolumeOver(id, threshold) => Json.obj("operation" -> "SubscribeVolumeOver", "id" -> id, "threshold" -> threshold)
      case SubscribeVolumeBelow(id, threshold) => Json.obj("operation" -> "SubscribeVolumeBelow", "id" -> id, "threshold" -> threshold)
      case Unsubscribe(id) => Json.obj("operation" -> "Unsubscribe", "id" -> id)
    }

    override def reads(json: JsValue): JsResult[Command] = {
      val idOption = (json \ "id").asOpt[Int]
      val operationOption = (json \ "operation").asOpt[String]
      val thresholdOption = (json \ "threshold").asOpt[BigDecimal]
      (idOption, operationOption, thresholdOption) match {
        case (Some(id), Some(operation), Some(threshold)) =>
          operation match {
            case "SubscribeBidOver" => JsSuccess(SubscribeBidOver(id, threshold))
            case "SubscribeAskBelow" => JsSuccess(SubscribeAskBelow(id, threshold))
            case "SubscribeVolumeOver" => JsSuccess(SubscribeVolumeOver(id, threshold))
            case "SubscribeVolumeBelow" => JsSuccess(SubscribeVolumeBelow(id, threshold))
            case _ => JsError("Unknown operation")
          }
        case (Some(id), Some(operation), None) =>
          operation match {
            case "SubscribeRateChange" => JsSuccess(SubscribeRateChange(id))
            case "Unsubscribe" => JsSuccess(Unsubscribe(id))
            case _ => JsError("Unknown operation or missing 'threshold' field")
          }
        case _ => JsError("Missing fields 'id' and/or 'operation'")
      }
    }
  }

  implicit val commandFrameFormatter = FrameFormatter.jsonFrame[Command]

  implicit val marketEventFormat = new Format[MarketEvent] {
    override def writes(o: MarketEvent): JsValue = o match {
      case o: OperationSuccessful => Json.obj("operation" -> "OperationSuccessful", "id" -> o.id)
      case o: Alarm => Json.obj("operation" -> "Alarm", "id" -> o.id, "value" -> o.value)
      case o: AllSubscriptions => Json.obj("operation" -> "AllSubscriptions", "subscriptions" -> o.subscriptions)
    }

    override def reads(json: JsValue): JsResult[MarketEvent] = ??? // not needed because it's out type
  }

  implicit val marketEventFrameFormatter = FrameFormatter.jsonFrame[MarketEvent]

  implicit val tokenReadsFormat = Json.reads[Token]
}

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object BtcWs extends Controller with Formats {
  def index(authToken: String) = WebSocket.tryAcceptWithActor[Command, MarketEvent] { implicit request =>
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
    case r: Command =>
      out ! OperationSuccessful(10)
  }
}
