package controllers

import akka.actor.{Props, Actor, ActorRef}
import akka.routing.{RemoveRoutee, ActorRefRoutee, AddRoutee}
import global.FlowInitializer
import metrics.common.{Counter, Value, Metric}
import play.api.Play.current
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._

object MetricsCollector extends Controller {
  implicit val valueJsonFormat = Json.format[Value]
  implicit val counterJsonFormat = Json.format[Counter]
  implicit val metricJsonFormat = new Format[Metric] {
    override def reads(json: JsValue): JsResult[Metric] = ??? // not needed
    override def writes(o: Metric): JsValue = o match {
      case c: Counter => counterJsonFormat.writes(c)
      case v: Value => valueJsonFormat.writes(v)
    }
  }
  implicit val formatter = FrameFormatter.jsonFrame[Metric]

  def index() = WebSocket.acceptWithActor[String, Metric] { implicit request =>
    WebSocketHandlerActor.props
  }
}

object WebSocketHandlerActor {
  def props(out: ActorRef) = Props(new WebSocketHandlerActor(out))
}

class WebSocketHandlerActor(out: ActorRef) extends Actor {
  override def preStart(): Unit = {
     router ! AddRoutee(routee)
  }

  override def postStop(): Unit = {
    router ! RemoveRoutee(routee)
  }

  override def receive: Receive = {
    case m: Metric => out ! m
  }

  private val routee = ActorRefRoutee(self)
  private val router = context.system.actorSelection(FlowInitializer.RouterPath)
}
