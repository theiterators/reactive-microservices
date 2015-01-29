import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.FlowMaterializer
import com.typesafe.config.ConfigFactory
import scala.concurrent.blocking
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.lifted.{ProvenShape, Tag}
import spray.json.DefaultJsonProtocol

case class Identity(id: Option[Long], createdAt: Long)

class Identities(tag: Tag) extends Table[Identity](tag, "identity") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def createdAt = column[Long]("created_at", O.NotNull)

  override def * : ProvenShape[Identity] = (id.?, createdAt) <>((Identity.apply _).tupled, Identity.unapply)
}

trait IdentityManagerJsonProtocols extends DefaultJsonProtocol {
  protected implicit val identityFormat = jsonFormat2(Identity.apply)
}

object IdentityManager extends App with IdentityManagerJsonProtocols {
  private val config = ConfigFactory.load()
  private val interface = config.getString("http.interface")
  private val port = config.getInt("http.port")
  private val dbUrl = config.getString("db.url")
  private val dbUser = config.getString("db.user")
  private val dbPassword = config.getString("db.password")

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = FlowMaterializer()
  private implicit val dispatcher = actorSystem.dispatcher

  private val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
  private val identities = TableQuery[Identities]

  private def getAllIdentities(): List[Identity] = {
    blocking {
      db.withSession { implicit s =>
        identities.list
      }
    }
  }

  private def saveIdentity(identity: Identity): Identity = {
    blocking {
      db.withSession { implicit s =>
        identities returning identities.map(_.id) into ((_, id) => identity.copy(id = Option(id))) += identity
      }
    }
  }

  Http().bind(interface = interface, port = port).startHandlingWith {
    logRequestResult("identity-manager") {
      path("identities") {
        pathEndOrSingleSlash {
          post {
            complete {
              val newIdentity = Identity(id = None, createdAt = System.currentTimeMillis())
              Created -> saveIdentity(newIdentity)
            }
          } ~
          get {
            complete {
              getAllIdentities()
            }
          }
        }
      }
    }
  }
}
