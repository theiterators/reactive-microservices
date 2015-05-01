package metrics.common

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.{RunnableFlow, Flow, Sink, Source}
import com.typesafe.config.Config
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat, _}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

sealed trait Metric {
  val path: String
  val timestamp: Long
}

case class Counter(override val path: String, count: Long, override val timestamp: Long = System.currentTimeMillis()) extends Metric

object Counter extends DefaultJsonProtocol {
  implicit val format = jsonFormat3(Counter.apply)
}

case class Value(override val path: String, value: Long, override val timestamp: Long = System.currentTimeMillis()) extends Metric

object Value extends DefaultJsonProtocol {
  implicit val format = jsonFormat3(Value.apply)
}

object Metric extends DefaultJsonProtocol {
  implicit val format = new RootJsonFormat[Metric] {
    override def write(metric: Metric): JsValue = {
      metric match {
        case counter: Counter => counter.toJson
        case value: Value => value.toJson
      }
    }

    override def read(json: JsValue): Metric = {
      Try(json.convertTo[Counter]).getOrElse(json.convertTo[Value])
    }
  }
}

trait Metrics {
  protected implicit val actorSystem: ActorSystem
  protected implicit val dispatcher: ExecutionContextExecutor
  protected implicit val materializer: FlowMaterializer
  protected val config: Config

  private lazy val metricsConnectionFlow = Http().outgoingConnection(config.getString("services.metrics-collector.host"),
                                                                     config.getInt("services.metrics-collector.port"))
  private lazy val metricsSource = Source.actorPublisher[Metric](MetricsManager.props)
  private lazy val requestFlow = Flow[Metric].map(m => RequestBuilding.Post("/metrics", m))
  private lazy val metricsFlow: RunnableFlow[ActorRef] = metricsSource.via(requestFlow).via(metricsConnectionFlow).to(Sink.onComplete { _ =>
    val metricsManagerRef = metricsFlow.run()
    metricsSupervisorRef ! MetricsSupervisor.NewMetricsManager(metricsManagerRef)
  })

  private lazy val metricsManagerRef = metricsFlow.run()

  private lazy val metricsSupervisorRef = actorSystem.actorOf(MetricsSupervisor.props(metricsManagerRef))

  def putMetric(metric: Metric): Unit = metricsSupervisorRef ! metric

  private class MetricsSupervisor(initial: ActorRef) extends Actor {
    override def receive: Receive = {
      case m: Metric => metricsManager ! m
      case MetricsSupervisor.NewMetricsManager(mm) => metricsManager = mm
    }

    private var metricsManager = initial
  }

  private object MetricsSupervisor {
    def props(initial: ActorRef) = Props(new MetricsSupervisor(initial))

    case class NewMetricsManager(metricsManager: ActorRef)
  }

  private class MetricsManager extends ActorPublisher[Metric] {
    override def receive: Receive = {
      case metric: Metric if buffer.size == MaxBufferSize =>
        // drop
      case metric: Metric =>
        if (buffer.isEmpty && totalDemand > 0) {
          onNext(metric)
        } else {
          buffer :+= metric
          deliverBuffer()
        }
      case Request(n) =>
        deliverBuffer()
      case Cancel => context.stop(self)
    }

    @tailrec
    private def deliverBuffer(): Unit = {
      if (totalDemand > 0) {
        if (totalDemand < Int.MaxValue) {
          val (use, keep) = buffer.splitAt(totalDemand.toInt)
          buffer = keep
          use.foreach(onNext)
        } else {
          val (use, keep) = buffer.splitAt(Int.MaxValue)
          buffer = keep
          use.foreach(onNext)
          deliverBuffer()
        }
      }
    }
  }

  private object MetricsManager {
    def props: Props = Props(new MetricsManager)
  }

  private val MaxBufferSize = 1024
  private var buffer = Vector.empty[Metric]
}