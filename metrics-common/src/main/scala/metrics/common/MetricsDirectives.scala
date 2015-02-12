package metrics.common

import akka.http.model.{HttpRequest, HttpResponse}
import akka.http.server.Directive0

case class RequestResponseStats(request: HttpRequest, response: HttpResponse, time: Long)

trait MetricsDirectives {
  import akka.http.server.directives.BasicDirectives._

  def measureRequestResponse(f: (RequestResponseStats => Unit)): Directive0 = {
    extractRequestContext.flatMap { ctx =>
      val start = System.currentTimeMillis()
      mapResponse { response =>
        val stop = System.currentTimeMillis()
        f(RequestResponseStats(ctx.request, response, stop - start))
        response
      }
    }
  }
}

object MetricsDirectives extends MetricsDirectives