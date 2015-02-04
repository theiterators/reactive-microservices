package data

import akka.actor.{Actor, ActorLogging, Props}
import data.UsersManager.LookupUser

trait UsersManagerConfig {
  def userConfig: UserActorConfig
}

object UsersManager {
  type UserId = Long

  case class LookupUser(id: UserId)

  def props(config: UsersManagerConfig) = Props(new UsersManager(config))
}

// ma utworzyc uzytkonika jesli on nie istnieje, a jesli istnieje to zwrocic do niego actor refa
class UsersManager(config: UsersManagerConfig) extends Actor with ActorLogging {

  override def receive: Receive = {
    case lu: LookupUser => lookupForUser(lu)
  }

  private def lookupForUser(lu: LookupUser) = {
    log.info(s"Lookup for a user ${lu.id}")
    val id = "user-" + lu.id
    val userActor = context child id match {
      case Some(user) =>
        log.info(s"User with id: ${lu.id} has been found!")
        user
      case None =>
        log.info(s"Creating new user with id: ${lu.id}!")
        context.actorOf(UserActor.props(lu.id, config.userConfig, sender()), id)
    }
    sender() ! WSMock.InitActorResponse(userActor)
  }
}
