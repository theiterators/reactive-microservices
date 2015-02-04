package data

import akka.actor.ActorSystem
import akka.http.model.{ContentTypes, HttpEntity}
import akka.http.unmarshalling.Unmarshal
import akka.stream.FlowMaterializer
import spray.json.DefaultJsonProtocol
import akka.http.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.{Future, ExecutionContext}

case class DataPack(max: BigDecimal, min: BigDecimal, last: BigDecimal, bid: BigDecimal,
                    ask: BigDecimal, vwap: BigDecimal, average: BigDecimal, volume: BigDecimal)

object DataImportService extends DefaultJsonProtocol {
  implicit val dataPackFomat = jsonFormat8(DataPack)

  private val url = "https://market.bitbay.pl/API/Public/BTCPLN/ticker.json"
  private val uaKey = "User-Agent"
  private val uaValue = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)"
}

class DataImportService(implicit actorSystem: ActorSystem, materializer: FlowMaterializer, ec: ExecutionContext) {
  import DataImportService._

  def importData: Future[DataPack] = {
    //FIXME ustawiac jakis sensowny timeout tu sie da?
    val connection = new java.net.URL(url).openConnection
    connection.setRequestProperty(uaKey, uaValue)

    val content = scala.io.Source.fromInputStream(connection.getInputStream).getLines.mkString
    Unmarshal(HttpEntity(ContentTypes.`application/json`,content)).to[DataPack]
  }
}
