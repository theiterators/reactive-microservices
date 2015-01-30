import spray.json._

trait JsonProtocols extends DefaultJsonProtocol {
  protected implicit val emailFormat = new JsonFormat[EmailAddress] {
    override def write(obj: EmailAddress): JsValue = JsString(obj.address)

    override def read(json: JsValue): EmailAddress = json match {
      case JsString(value) => EmailAddress(value)
      case _ => deserializationError("Email address expected")
    }
  }

  protected implicit val passwordRegisterRequestFormat = jsonFormat2(PasswordRegisterRequest)
  protected implicit val passwordLoginRequestFormat = jsonFormat2(PasswordLoginRequest)
  protected implicit val resetRequestFormat = jsonFormat2(PasswordResetRequest)
  protected implicit val identityFormat = jsonFormat1(Identity)
  protected implicit val tokenFormat = jsonFormat4(Token)
  protected implicit val loginRequestFormat = jsonFormat2(InternalLoginRequest)
  protected implicit val reloginRequestFormat = jsonFormat2(InternalReloginRequest)
}
