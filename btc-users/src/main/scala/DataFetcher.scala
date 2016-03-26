import akka.actor.{ActorLogging, Props, Actor, ActorRef}
import com.ning.http.client.AsyncHttpClientConfig.Builder
import play.api.libs.json.Json
import play.api.libs.ws.ning.NingWSClient
import UserHandler.Ticker

class DataFetcher(broadcaster: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case DataFetcher.Tick =>
      client.url(url).get().map { response =>
        if (response.status == 200) {
          val ticker = Json.parse(response.body).as[Ticker]
          log.debug(s"Broadcasting ticker $ticker")
          broadcaster ! ticker
        }
      }.onFailure { case t => log.warning(s"Requesting ticker failed because ${t.getMessage}") }
  }

  private implicit val tickerFormat = Json.format[Ticker]
  private implicit val dispatcher = context.dispatcher
  private val url = "https://bitbay.net/API/Public/BTCUSD/ticker.json"
  private val client = new NingWSClient(new Builder().build())
}

object DataFetcher {
  case object Tick

  def props(broadcaster: ActorRef): Props = Props(new DataFetcher(broadcaster))
}
