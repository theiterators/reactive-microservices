package global

import akka.actor.{PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.routing.{RemoveRoutee, NoRoutee, AddRoutee, BroadcastGroup}
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{ActorSubscriber, RequestStrategy, WatermarkRequestStrategy}
import akka.stream.scaladsl._
import metrics.common.{Value, Counter, Metric}
import play.api._
import play.api.libs.concurrent.Akka
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.Macros
import reactivemongo.core.nodeset.Authenticate
import scala.collection.immutable.Seq
import scala.concurrent.Future

object FlowInitializer extends GlobalSettings {
  override def onStart(a: Application): Unit = {
    implicit val app = a
    implicit val actorSystem = Akka.system
    implicit val materializer = ActorFlowMaterializer()
    implicit val dispatcher = actorSystem.dispatcher

    val interface = app.configuration.getString("metrics-collector.interface").get
    val port = app.configuration.getInt("metrics-collector.port").get

    val requestFlow = Flow() { implicit b =>
      import FlowGraph.Implicits._

      val wsSubscriber = b.add(Sink.actorSubscriber[Metric](ActorsWs.props))
      val journalerSubscriber = b.add(Sink.actorSubscriber[Metric](ActorJournaler.props(app.configuration)))

      val requestResponseFlow = b.add(Flow[HttpRequest].map(_ => HttpResponse(OK)))
      val requestMetricFlow = b.add(Flow[HttpRequest].mapAsync(1) { request =>
        Unmarshal(request.entity).to[Metric].map(Seq(_)).fallbackTo(Future.successful(Seq.empty[Metric]))
      }.mapConcat(identity))

      val broadcastRequest = b.add(Broadcast[HttpRequest](2))
      val broadcastMetric = b.add(Broadcast[Metric](2))

      broadcastRequest ~> requestResponseFlow
      broadcastRequest ~> requestMetricFlow ~> broadcastMetric ~> wsSubscriber
                                               broadcastMetric ~> journalerSubscriber

      (broadcastRequest.in, requestResponseFlow.outlet)
    }

    Http().bindAndHandle(interface = interface, port = port, handler = requestFlow)

    val router = actorSystem.actorOf(BroadcastGroup(List.empty[String]).props(), RouterName)
    router ! AddRoutee(NoRoutee) // prevents router from terminating when last websocket disconnects
  }

  override def onStop(a: Application): Unit = {
    val router = Akka.system(a).actorOf(BroadcastGroup(List.empty[String]).props(), RouterName)
    router ! RemoveRoutee(NoRoutee)
    router ! PoisonPill
  }

  private val RouterName = "BroadcastRouter"
  val RouterPath = s"/user/$RouterName"
}

class ActorsWs extends ActorSubscriber {
  override protected def requestStrategy: RequestStrategy = new WatermarkRequestStrategy(1024)

  override def receive: Receive = {
    case OnNext(m: Metric) => router ! m
    case OnComplete => self ! PoisonPill
  }

  private val router = context.system.actorSelection(FlowInitializer.RouterPath)
}

object ActorsWs {
  def props: Props = Props(new ActorsWs)
}

class ActorJournaler(configuration: Configuration) extends ActorSubscriber {
  private val mongoHost = configuration.getString("mongo.host").get
  private val mongoDb = configuration.getString("mongo.db").get
  private val mongoUser = configuration.getString("mongo.user").get
  private val mongoPassword = configuration.getString("mongo.password").get
  private implicit val dispatcher = context.dispatcher
  private val mongoConnection = (new MongoDriver).connection(nodes = List(mongoHost), authentications = List(Authenticate(mongoDb, mongoUser, mongoPassword)))
  private val mongoDatabase = mongoConnection(mongoDb)
  private val metrics: BSONCollection = mongoDatabase("metrics")
  implicit val counterMongoHandler = Macros.handler[Counter]
  implicit val valueMongoHandler = Macros.handler[Value]

  override protected def requestStrategy: RequestStrategy = new WatermarkRequestStrategy(1024)

  override def receive: Receive = {
    case OnNext(c: Counter) => metrics.insert(c)
    case OnNext(v: Value) => metrics.insert(v)
    case OnComplete => self ! PoisonPill
  }
}

object ActorJournaler {
  def props(configuration: Configuration): Props = Props(new ActorJournaler(configuration))
}