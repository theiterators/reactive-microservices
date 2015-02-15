package controllers

import akka.actor._
import btc.common.UserManagerMessages.LookupUser
import btc.common.UserHandlerMessages._
import btc.common.WebSocketHandlerMessages._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import WebSocketHandler._

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
        val usersManager = Akka.system.actorSelection(current.configuration.getString("services.btc-users.users-manager-path").get)
        Right(WebSocketHandler.props(token, usersManager, webSocketHandlerTimeout, _: ActorRef))
      } else {
        Left(Unauthorized("Token expired or not found"))
      }
    }
  }

  private def tokenManagerUrl(authToken: String) = WS.url(s"http://$tokenManagerHost:$tokenManagerPort/tokens/$authToken")
  private val tokenManagerHost = current.configuration.getString("services.token-manager.host").get
  private val tokenManagerPort = current.configuration.getInt("services.token-manager.port").get
  private val webSocketHandlerTimeout = current.configuration.getLong("web-socket-handler.timeout").get.millis
}

object WebSocketHandler {
  case object Timeout
  case object KeepAlive

  def props(token: Token, usersManager: ActorSelection, keepAliveTimeout: FiniteDuration, out: ActorRef) = {
    Props(new WebSocketHandler(token, usersManager, keepAliveTimeout, out))
  }
}

class WebSocketHandler(token: Token, usersManager: ActorSelection, keepAliveTimeout: FiniteDuration, out: ActorRef) extends Actor with ActorLogging {
  override def preStart(): Unit = requestHandlerWithTimeout()

  override def receive: Receive = waitForHandler

  private def waitForHandler: Receive = {
    case InitActorResponse(handler: ActorRef) =>
      log.info(s"Got handler for user ${token.identityId}")
      handler ! QuerySubscriptions
      context.become(waitForSubscriptions(handler))
    case Timeout =>
      log.warning(s"Timeout while waiting for handler for user ${token.identityId}, closing connection")
      self ! PoisonPill
  }

  private def waitForSubscriptions(handler: ActorRef): Receive = {
    case subs @ AllSubscriptions(subscriptions) =>
      log.info(s"Got subscriptions $subscriptions for user ${token.identityId}")
      out ! subs
      scheduleHeartbeatAndKeepAlive(handler)
      context.become(handleUser(handler, subscriptions))
    case Timeout =>
      log.warning(s"Timeout while waiting for subscriptions for user ${token.identityId}, closing connection")
      self ! PoisonPill
  }

  private def handleUser(handler: ActorRef, subscriptions: Seq[Subscribe]): Receive = {
    case command: Command =>
      log.debug(s"Got command $command from user ${token.identityId}")
      handler ! command
    case event: MarketEvent =>
      log.debug(s"Got market event $event for user ${token.identityId}")
      out ! event
    case Heartbeat =>
      log.debug(s"Got heartbeat for user ${token.identityId}")
      lastHeartBeat = System.currentTimeMillis()
      scheduleHeartbeatAndKeepAlive(handler)
    case KeepAlive if System.currentTimeMillis() - lastHeartBeat > keepAliveTimeout.toMillis =>
      log.warning(s"Timeout while handling user ${token.identityId}, restarting")
      requestHandlerWithTimeout()
      context.become(waitForHandler, discardOld = true)
  }

  private def requestHandlerWithTimeout(): Unit = {
    log.info(s"Requesting handler for user ${token.identityId}")
    usersManager ! LookupUser(token.identityId)
    context.system.scheduler.scheduleOnce(keepAliveTimeout, self, Timeout)
  }

  private def scheduleHeartbeatAndKeepAlive(handler: ActorRef): Unit = {
    context.system.scheduler.scheduleOnce(keepAliveTimeout / 3, handler, Heartbeat)
    context.system.scheduler.scheduleOnce(keepAliveTimeout, self, KeepAlive)
  }

  private var lastHeartBeat = System.currentTimeMillis()
}
