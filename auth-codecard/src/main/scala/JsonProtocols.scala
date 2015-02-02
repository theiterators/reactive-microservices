import spray.json.DefaultJsonProtocol

trait JsonProtocols extends DefaultJsonProtocol {
  protected implicit val identityFormat = jsonFormat1(Identity)
  protected implicit val tokenFormat = jsonFormat4(Token)
  protected implicit val codeCardFormat = jsonFormat3(CodeCard)
  protected implicit val authEntryFormat = jsonFormat4(AuthEntry)
  protected implicit val internalLoginRequestFormat = jsonFormat2(InternalLoginRequest)
  protected implicit val internalReloginRequestFormat = jsonFormat2(InternalReloginRequest)
  protected implicit val registerResponseFormat = jsonFormat2(RegisterResponse)
  protected implicit val loginRequestFormat = jsonFormat4(LoginRequest)
  protected implicit val activateCodeRequestFormat = jsonFormat1(ActivateCodeRequest)
  protected implicit val activateCodeResponseFormat = jsonFormat2(ActivateCodeResponse)
  protected implicit val getCodeCardRequestFormat = jsonFormat1(GetCodeCardRequest)
  protected implicit val getCodeCardResponseFormat = jsonFormat2(GetCodeCardResponse)
}
