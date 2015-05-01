import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorFlowMaterializer
import metrics.common.{Value, RequestResponseStats, Counter, Metrics}
import metrics.common.MetricsDirectives._

case class LoginRequest(identityId: Long, authMethod: String)

case class ReloginRequest(tokenValue: String, authMethod: String)

case class Token(value: String, validTo: Long, identityId: Long, authMethods: Set[String])

object TokenManager extends App with JsonProtocols with Config with Metrics {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val repository = new Repository
  val service = new Service(repository)

  def putMetricForRequestResponse(requestStats: RequestResponseStats): Unit = {
    val method = requestStats.request.method.name.toLowerCase
    putMetric(Value(s"token-manager.$method.time", requestStats.time))
  }

  Http().bindAndHandle(interface = interface, port = port, handler = {
    (measureRequestResponse(putMetricForRequestResponse) & logRequestResult("token-manager")) {
      pathPrefix("tokens") {
        (post & pathEndOrSingleSlash & entity(as[LoginRequest])) { loginRequest =>
          complete {
            putMetric(Counter("token-manager.post", 1))
            service.login(loginRequest).map(token => Created -> token)
          }
        } ~
        (patch & pathEndOrSingleSlash & entity(as[ReloginRequest])) { reloginRequest =>
          complete {
            service.relogin(reloginRequest).map[ToResponseMarshallable] {
              case Some(token) =>
                putMetric(Counter("token-manager.patch", 1))
                OK -> token
              case None =>
                putMetric(Counter("token-manager.patch", -1))
                NotFound -> "Token expired or not found"
            }
          }
        } ~
        (path(Segment) & pathEndOrSingleSlash) { tokenValue =>
          get {
            complete {
              service.findAndRefreshToken(tokenValue).map[ToResponseMarshallable] {
                case Some(token) =>
                  putMetric(Counter("token-manager.get", 1))
                  OK -> token
                case None =>
                  putMetric(Counter("token-manager.get", -1))
                  NotFound -> "Token expired or not found"
              }
            }
          } ~
          delete {
            complete {
              service.logout(tokenValue)
              putMetric(Counter("token-manager.delete", 1))
              OK
            }
          }
        }
      }
    }
  })
}

