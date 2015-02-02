import scala.concurrent.blocking
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.meta.MTable
import scala.slick.lifted.{ProvenShape, Tag}

case class EmailAddress(address: String) extends MappedTo[String] {
  override val value: String = address

  require(EmailAddress.isValid(address), "Invalid email address format")
}

object EmailAddress {
  def isValid(email: String): Boolean = EmailRegex.pattern.matcher(email.toUpperCase).matches()

  private val EmailRegex = """\b[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*\b""".r
}

case class AuthEntry(id: Option[Long], identityId: Long, createdAt: Long, email: EmailAddress, password: String)

class AuthEntries(tag: Tag) extends Table[AuthEntry](tag, "auth_entry") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def identityId = column[Long]("identity_id", O.NotNull)

  def createdAt = column[Long]("created_at", O.NotNull)

  def email = column[EmailAddress]("email", O.NotNull)

  def password = column[String]("password", O.NotNull)

  override def * : ProvenShape[AuthEntry] = (id.?, identityId, createdAt, email, password) <> (AuthEntry.tupled, AuthEntry.unapply)
}

class Repository extends Config {
  def createAuthEntry(entry: AuthEntry) = {
    blocking {
      db.withSession { implicit session =>
        authEntries.insert(entry).run
      }
    }
  }

  def updateAuthEntry(entry: AuthEntry) = {
    blocking {
      db.withSession { implicit session =>
        authEntries.filter(_.id === entry.id.get).update(entry)
      }
    }
  }

  def findAuthEntry(email: EmailAddress): Option[AuthEntry] = {
    blocking {
      db.withSession { implicit session =>
        byEmailCompiled(email).firstOption
      }
    }
  }

  private def byEmailQuery(email: Column[EmailAddress]) = authEntries.filter(_.email === email)
  private val byEmailCompiled = Compiled(byEmailQuery _)

  private val authEntries = TableQuery[AuthEntries]

  private val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
}