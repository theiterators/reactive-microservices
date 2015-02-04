package data

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ClusterClient
import data.UsersManager.LookupUser
import data.WSMock.{InitActorResponse, InitActor, SecondMessage}

object WSMock {
  case object InitActor
  case object SecondMessage

  case class InitActorResponse(user: ActorRef)

  def props(id: Long, usersManager: ActorRef) = Props(new WSMock(id, usersManager))
}

class WSMock(id: Long, usersManager: ActorRef) extends Actor with ActorLogging {

  var userActor: ActorRef = _

  def handleInitActor(response: InitActorResponse): Unit = {
    println(s"### IAR: ${response.user}")
    userActor = response.user
  }

  override def receive: Receive = {
    case InitActor => usersManager ! ClusterClient.Send("/user/users-manager", LookupUser(id), false)
    case iar: InitActorResponse => handleInitActor(iar)
    case SecondMessage => {
      userActor ! UserActor.MessageFromWS

      userActor ! UserActor.SubscribeBidOver(5, BigDecimal(5))
      userActor ! UserActor.SubscribeAskBelow(4, BigDecimal(10000))
      userActor ! UserActor.UnSubscribe(5)
      userActor ! UserActor.HeartBeat
    }
    case x: Any => println(s"WSMock received message: ${x}")
  }
}
