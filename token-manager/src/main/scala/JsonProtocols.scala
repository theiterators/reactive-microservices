import spray.json.DefaultJsonProtocol

trait JsonProtocols extends DefaultJsonProtocol {
  protected implicit val tokenFormat = jsonFormat4(Token.apply)
  protected implicit val reloginRequestFormat = jsonFormat2(ReloginRequest.apply)
  protected implicit val loginRequestFormat = jsonFormat2(LoginRequest.apply)
}