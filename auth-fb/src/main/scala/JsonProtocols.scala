import spray.json.DefaultJsonProtocol

trait JsonProtocols extends DefaultJsonProtocol {
  protected implicit val authResponseFormat = jsonFormat1(AuthResponse.apply)
  protected implicit val identityFormat = jsonFormat1(Identity.apply)
  protected implicit val loginRequestFormat = jsonFormat2(InternalLoginRequest.apply)
  protected implicit val reloginRequestFormat = jsonFormat2(InternalReloginRequest.apply)
  protected implicit val tokenFormat = jsonFormat4(Token.apply)
}