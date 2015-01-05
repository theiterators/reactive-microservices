import spray.json.DefaultJsonProtocol

trait AuthCodeJsonProtocol extends DefaultJsonProtocol {
  implicit val identityFormat = jsonFormat1(Identity)
  implicit val tokenFormat = jsonFormat4(Token)
  implicit val codeCardFormat = jsonFormat2(CodeCard)
  implicit val authEntryFormat = jsonFormat4(AuthEntry)
  implicit val internalLoginRequestFormat = jsonFormat2(InternalLoginRequest)
  implicit val internalReloginRequestFormat = jsonFormat2(InternalReloginRequest)
  implicit val registerResponseFormat = jsonFormat3(RegisterResponse)
  implicit val loginRequestFormat = jsonFormat4(LoginRequest)
  implicit val activateCodeRequestFormat = jsonFormat1(ActivateCodeRequest)
  implicit val activateCodeResponseFormat = jsonFormat2(ActivateCodeResponse)
  implicit val getCodeCardRequestFormat = jsonFormat1(GetCodeCardRequest)
  implicit val getCodeCardResponseFormat = jsonFormat2(GetCodeCardResponse)
}
