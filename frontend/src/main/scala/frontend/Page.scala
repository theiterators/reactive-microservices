package frontend

import scalatags.Text.all._

object Page {
  val loginRegisterForm = {
    div(`class` := "container",
      div(`class` := "row",
        div(`class` := "col-md-6 col-md-offset-3",
          div(`class` := "panel panel-login",
            div(`class` := "panel-heading",
              div(`class` := "row",
                div(`class` := "col-xs-6",
                  a(href := "#", `class` := "active", id := "login-form-link", "Login")
                ),
                div(`class` := "col-xs-6",
                  a(href := "#", id := "register-form-link", "Register")
                )
              ),
              hr(
              ),
              div(`class` := "panel-body",
                div(`class` := "row",
                  div(`class` := "col-lg-12",
                    form(id := "login-form", style := "display: block;",
                      div(`class` := "form-group",
                        input(`type` := "text", name := "login-email", id := "login-email", tabindex := "1", `class` := "form-control", placeholder := "Email Address", value := ""
                        ),
                        div(`class` := "form-group",
                          input(`type` := "password", name := "login-password", id := "login-password", tabindex := "2", `class` := "form-control", placeholder := "Password"
                          ),
                          div(`class` := "form-group",
                            div(`class` := "row",
                              div(`class` := "col-sm-6 col-sm-offset-3",
                                input(`type` := "submit", name := "login-submit", id := "login-submit", tabindex := "4", `class` := "form-control btn btn-login", value := "Log In"
                                )
                              )
                            )
                          )
                        )
                      )
                    ),
                    form(id := "register-form", style := "display: none;",
                      div(`class` := "form-group",
                        input(`type` := "email", name := "register-email", id := "register-email", tabindex := "1", `class` := "form-control", placeholder := "Email Address", value := ""
                        ),
                        div(`class` := "form-group",
                          input(`type` := "password", name := "register-password", id := "register-password", tabindex := "2", `class` := "form-control", placeholder := "Password"
                          ),
                          div(`class` := "form-group",
                            input(`type` := "password", name := "register-confirm-password", id := "register-confirm-password", tabindex := "2", `class` := "form-control", placeholder := "Confirm Password"
                            ),
                            div(`class` := "form-group",
                              div(`class` := "row",
                                div(`class` := "col-sm-6 col-sm-offset-3",
                                  input(`type` := "button", name := "register-submit", id := "register-submit", tabindex := "4", `class` := "form-control btn btn-register", value := "Register Now"
                                  )
                                )
                              )
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    ).toString()
  }

  val marketInfoTable = {
    div(`class` := "container",
      h2("Table"),
      table(`class` := "table",
        thead(
          tr(
            th("operation"),
            th("id"),
            th("value")
          )
        ),
        tbody(id := "message-log")
      )
    ).toString()
  }
}
