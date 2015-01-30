import scala.slick.lifted.{ProvenShape, Tag}
import scala.slick.driver.PostgresDriver.simple._
import scala.concurrent._

case class AuthEntry(userIdentifier: String, identityId: Long, createdAt: Long, lastCard: Long)

case class Code(userIdentifier: String, cardIndex: Long, codeIndex: Long, code: String, createdAt: Long, activatedAt: Option[Long] = None, usedAt: Option[Long] = None)

class AuthEntries(tag: Tag) extends Table[AuthEntry](tag, "auth_entry") {
  def userIdentifier = column[String]("user_identifier", O.PrimaryKey, O.NotNull)
  def identityId = column[Long]("identity_id", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def lastCard = column[Long]("last_card")
  override def * : ProvenShape[AuthEntry] = (userIdentifier, identityId, createdAt, lastCard) <> (AuthEntry.tupled, AuthEntry.unapply)
}

class Codes(tag: Tag) extends Table[Code](tag, "code") {
  def userIdentifier = column[String]("user_identifier", O.NotNull)
  def cardIndex = column[Long]("card_index", O.NotNull)
  def codeIndex = column[Long]("code_index", O.NotNull)
  def code = column[String]("code", O.NotNull)
  def createdAt = column[Long]("created_at", O.NotNull)
  def activatedAt = column[Option[Long]]("activated_at")
  def usedAt = column[Option[Long]]("used_at")
  override def * : ProvenShape[Code] = (userIdentifier, cardIndex, codeIndex, code, createdAt, activatedAt, usedAt) <> (Code.tupled, Code.unapply)
}

class Repository extends Config {
  def useCode(userIdentifier: String, cardIdx: Long, codeIdx: Long, code: String): Int = {
    blocking {
      db.withSession { implicit s =>
        codesQuery.filter(codeQ => codeQ.userIdentifier === userIdentifier &&
          codeQ.cardIndex === cardIdx &&
          codeQ.codeIndex === codeIdx &&
          codeQ.code === code &&
          codeQ.usedAt.isEmpty === true &&
          codeQ.activatedAt >= (System.currentTimeMillis - codeActiveTime))
          .map(_.usedAt).update(Some(System.currentTimeMillis))
      }
    }
  }

  def getIdentity(userIdentifier: String): Long = {
    blocking {
      db.withSession { implicit s =>
        authEntriesQuery.filter(line => line.userIdentifier === userIdentifier).map(_.identityId).first
      }
    }
  }

  def getNextCardIndex(userIdentifier: String): Long = {
    blocking {
      db.withSession { implicit s =>
        s.withTransaction {
          val next = authEntriesQuery.filter(line => line.userIdentifier === userIdentifier).map(_.lastCard).first + 1
          authEntriesQuery.filter(line => line.userIdentifier === userIdentifier).map(_.lastCard).update(next)
          next
        }
      }
    }
  }

  def saveAuthEntryAndCodeCard(authEntry: AuthEntry, codeCard: CodeCard): Unit = {
    blocking {
      db.withSession { implicit s =>
        s.withTransaction {
          authEntriesQuery += authEntry
          codeCard.codes.zipWithIndex.map { case (code, idx) =>
            codesQuery += Code(codeCard.userIdentifier, codeCard.id, idx.toLong, code, System.currentTimeMillis())
          }
        }
      }
    }
  }

  def getInactiveCodesForUser(userIdentifier: String): Seq[Code] = {
    blocking {
      db.withSession { implicit s =>
        codesQuery.filter(code => code.userIdentifier === userIdentifier && code.activatedAt.isEmpty === true).list
      }
    }
  }

  def activateCode(userIdentifier: String, cardIndex: Long, codeIndex: Long): Int = {
    blocking {
      db.withSession { implicit s =>
        codesQuery.filter(code => code.userIdentifier === userIdentifier &&
          code.cardIndex === cardIndex &&
          code.codeIndex === codeIndex).map(_.activatedAt).update(Some(System.currentTimeMillis))
      }
    }
  }

  private val codesQuery = TableQuery[Codes]
  private val authEntriesQuery = TableQuery[AuthEntries]

  private val db = Database.forURL(url = dbUrl, user = dbUser, password = dbPassword, driver = "org.postgresql.Driver")
}
