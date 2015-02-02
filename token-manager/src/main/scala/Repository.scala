import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.{Macros, BSONDocument}
import reactivemongo.core.nodeset.Authenticate
import scala.concurrent.{ExecutionContext, Future}

class Repository(implicit ec: ExecutionContext) extends Config {
  def insertToken(token: Token): Future[Token] = tokens.insert(token).map(_ => token)

  def updateTokenByValue(value: String, token: Token): Future[Int] = tokens.update(BSONDocument("value" -> value), token).map(_.updated)

  def deleteTokenByValue(value: String): Future[Int] = tokens.remove(BSONDocument("value" -> value)).map(_.updated)

  def findValidTokenByValue(value: String): Future[Option[Token]] = {
    tokens.find(BSONDocument("value" -> value, "validTo" -> BSONDocument("$gt" -> System.currentTimeMillis()))).cursor[Token].headOption
  }

  def addMethodToValidTokenByValue(value: String, method: String): Future[Option[Token]] = {
    tokens.update(BSONDocument("value" -> value), BSONDocument("$addToSet" -> BSONDocument("authMethods" -> method))).flatMap { lastError =>
      if (lastError.updated > 0) findValidTokenByValue(value) else Future.successful(None)
    }
  }

  private implicit val tokenHandler = Macros.handler[Token]
  private val mongoConnection = (new MongoDriver).connection(nodes = List(mongoHost), authentications = List(Authenticate(mongoDb, mongoUser, mongoPassword)))
  private val mongoDatabase = mongoConnection(mongoDb)
  private val tokens: BSONCollection = mongoDatabase("tokens")
}
