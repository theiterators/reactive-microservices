package tutorial.webapp

import org.scalajs.dom.raw.{Event, MessageEvent, WebSocket}
import org.scalajs.jquery.{JQueryXHR, JQueryAjaxSettings, jQuery}
import upickle.default._
import scala.scalajs.js
import js.JSApp
import js.Dynamic.literal

case class Credentials(email: String, password: String)
case class Message(operation: String, id: Int, value: Double)
case class Token(value: String)

object TutorialApp extends JSApp {

  def main(): Unit = {
    jQuery(setupUI _)
  }

  def setupUI(): Unit = {
    jQuery("#register-submit").click(register _)
    jQuery("#login-submit").click(login _)
  }

  def register(): Unit = {
    val email = jQuery("#register-email").value().toString
    val password = jQuery("#register-password").value().toString
    val confirmPassword = jQuery("#register-confirm-password").value().toString
    assert(password == confirmPassword)
    val credentials = Credentials(email = email, password = password)
    jQuery.ajax(literal(
      url = "http://localhost:1337/localhost:8002/register/password",
      contentType = "application/json; charset=utf-8",
      data = write(credentials),
      dataType = "json",
      `type` = "POST"
    ).asInstanceOf[JQueryAjaxSettings])
  }

  def login(): Unit = {
    val email = jQuery("#login-email").value().toString
    val password = jQuery("#login-password").value().toString
    val credentials = Credentials(email = email, password = password)
    jQuery.ajax(literal(
      url = "http://localhost:1337/localhost:8002/login/password",
      contentType = "application/json; charset=utf-8",
      data = write(credentials),
      dataType = "json",
      `type` = "POST",
      success = (data: js.Any, textStatus: String, jqXHR: JQueryXHR) => {
        println(jqXHR.responseText)
        val token = read[Token](jqXHR.responseText)
        println(token)
        val ws = new WebSocket(url = s"ws://localhost:9000/btc/${token.value}")
        ws.onmessage = { m: MessageEvent =>
          val message = read[Message](m.data.toString)
          println(message)
          jQuery("#message-log").append(
            s"""<tr>
              <td>${message.operation}</td>
              <td>${message.id}</td>
              <td>${message.value}</td>
            </tr>"""
          )}
        ws.onopen = { e: Event => ws.send("{\"id\":1,\"operation\":\"SubscribeRateChange\"}") }
      }
    ).asInstanceOf[JQueryAjaxSettings])
  }
}