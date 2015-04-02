import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.stream.ActorFlowMaterializer
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
  val config = ConfigFactory.load()
  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")
  val dbUrl = config.getString("db.url")
  val dbUser = config.getString("db.user")
  val dbPassword = config.getString("db.password")

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorFlowMaterializer()
  implicit val dispatcher = actorSystem.dispatcher

  val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
  val identities = TableQuery[Identities]

  def getAllIdentities(): List[Identity] = {
    blocking {
      db.withSession { implicit s =>
        identities.list
      }
    }
  }

  def saveIdentity(identity: Identity): Identity = {
    blocking {
      db.withSession { implicit s =>
        identities returning identities.map(_.id) into ((_, id) => identity.copy(id = Option(id))) += identity
      }
    }
  }

  Http().bindAndHandle(interface = interface, port = port, handler = {
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
  })
}
