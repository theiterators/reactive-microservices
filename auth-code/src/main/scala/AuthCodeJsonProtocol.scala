import spray.json.DefaultJsonProtocol

trait AuthCodeJsonProtocol extends DefaultJsonProtocol {
  protected implicit val identityFormat = jsonFormat1(Identity)
  protected implicit val tokenFormat = jsonFormat4(Token)
  protected implicit val internalLoginRequestFormat = jsonFormat2(InternalLoginRequest)
  protected implicit val internalReloginRequestFormat = jsonFormat2(InternalReloginRequest)
}
