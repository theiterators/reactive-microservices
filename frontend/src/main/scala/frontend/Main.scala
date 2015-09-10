package frontend

import org.scalajs.dom.raw.{Event, MessageEvent, WebSocket}
import org.scalajs.jquery.{JQueryXHR, JQueryAjaxSettings, jQuery}
import upickle.default._
import scala.scalajs.js
import js.JSApp
import js.Dynamic.literal
import scalatags.Text.all._

case class Credentials(email: String, password: String)

case class Message(operation: String, id: Int, value: Double)

case class Token(value: String)

object Main extends JSApp {

  def main(): Unit = {
    jQuery(setupUI _)
  }

  def setupUI(): Unit = {
    jQuery("body").append(Page.loginRegisterForm)
    jQuery("body").append(Page.marketInfoTable)
    jQuery("#register-submit").click(register _)
    jQuery("#login-submit").click(login _)
    jQuery("#login-form-link").click(activeLoginForm _)
    jQuery("#register-form-link").click(activeRegisterForm _)
  }

  def activeLoginForm(): Unit = {
    jQuery("#login-form").delay(100).fadeIn(100)
    jQuery("#register-form").fadeOut(100)
    jQuery("#register-form-link").removeClass("active")
    jQuery("#login-form").addClass("active")
  }

  def activeRegisterForm(): Unit = {
    jQuery("#register-form").delay(100).fadeIn(100)
    jQuery("#login-form").fadeOut(100)
    jQuery("#login-form-link").removeClass("active")
    jQuery("#register-form").addClass("active")
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
          jQuery("#message-log").append(
            tr(
              td(message.operation),
              td(message.id),
              td(message.value)
            ).toString
          )
        }
        ws.onopen = { e: Event => ws.send("{\"id\":1,\"operation\":\"SubscribeRateChange\"}") }
      }
    ).asInstanceOf[JQueryAjaxSettings])
  }
}