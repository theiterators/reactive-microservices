import sbt._

object Root extends Build {
    lazy val tokenManager = Project(id = "token-manager", base = file("token-manager"))
    lazy val authFb = Project(id = "auth-fb", base = file("auth-fb"))
    lazy val identityManager = Project(id = "identity-manager", base = file("identity-manager"))
}
