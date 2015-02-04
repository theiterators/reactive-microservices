import com.typesafe.config.ConfigFactory

trait Config {
  protected val config = ConfigFactory.load()
  protected val interface = config.getString("http.interface")
  protected val port = config.getInt("http.port")
  protected val redisHost = config.getString("redis.host")
  protected val redisPort = config.getInt("redis.port")
  protected val redisPassword = config.getString("redis.password")
  protected val redisDb = config.getInt("redis.db")

  protected val identityManagerHost = config.getString("services.identity-manager.host")
  protected val identityManagerPort = config.getInt("services.identity-manager.port")
  protected val tokenManagerHost = config.getString("services.token-manager.host")
  protected val tokenManagerPort = config.getInt("services.token-manager.port")
}