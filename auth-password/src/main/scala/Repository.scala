import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.meta.MTable
import scala.slick.lifted.{ProvenShape, Tag}

case class EmailAddress(address: String) extends MappedTo[String] {
  override val value: String = address

  require(EmailAddress.isValid(address), "Invalid email address format")
}

object EmailAddress {
  def isValid(email: String): Boolean = EmailRegex.pattern.matcher(email.toUpperCase).matches()

  private val EmailRegex = """\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}\b""".r // NOTE: only uppercase matching
}

case class AuthEntry(id: Option[Long], identityId: Long, createdAt: Long, email: EmailAddress, password: String)

class AuthEntries(tag: Tag) extends Table[AuthEntry](tag, "auth_entry") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def identityId = column[Long]("identity_id", O.NotNull)

  def createdAt = column[Long]("created_at", O.NotNull)

  def email = column[EmailAddress]("email", O.NotNull)

  def password = column[String]("password", O.NotNull)

  override def * : ProvenShape[AuthEntry] =
    (id.?, identityId, createdAt, email, password) <> ((AuthEntry.apply _).tupled, AuthEntry.unapply)
}

object Repository extends AuthPasswordConfig {
  private val authEntries = TableQuery[AuthEntries]

  private val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")

  db.withSession { implicit session =>
    if (MTable.getTables("auth_entry").list.isEmpty) {
      authEntries.ddl.create
    }
  }

  def exists(email: EmailAddress): Boolean = {
    db.withSession { implicit session =>
      byEmailCompiled(email).firstOption match {
        case Some(_) => true
        case None => false
      }
    }
  }

  def save(entry: AuthEntry) = {
    db.withSession { implicit session =>
      authEntries.insert(entry).run
    }
  }

  def get(email: EmailAddress): Option[AuthEntry] = {
    db.withSession { implicit session =>
      byEmailCompiled(email).firstOption
    }
  }

  private def byEmailQuery(email: Column[EmailAddress]) = authEntries.filter(_.email === email)

  private val byEmailCompiled = Compiled(byEmailQuery _)

}