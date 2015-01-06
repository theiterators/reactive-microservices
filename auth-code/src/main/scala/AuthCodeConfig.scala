import com.typesafe.config.ConfigFactory

trait AuthCodeConfig {
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")

  val dbUrl = config.getString("db.url")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")

  val identityManagerHost = config.getString("services.identity-manager.host")
  val identityManagerPort = config.getInt("services.identity-manager.port")
  val tokenManagerHost = config.getString("services.token-manager.host")
  val tokenManagerPort = config.getInt("services.token-manager.port")
}
