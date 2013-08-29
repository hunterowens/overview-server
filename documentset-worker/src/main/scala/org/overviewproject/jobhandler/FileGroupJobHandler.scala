package org.overviewproject.jobhandler

import scala.language.postfixOps
import akka.actor._
import scala.concurrent.{ future, Future }
import scala.concurrent.duration._
import FileGroupJobHandlerFSM._
import scala.util.{ Failure, Success }
import org.overviewproject.util.Logger

object FileGroupJobHandlerProtocol {
  case object ListenForFileGroupJobs
  case class ConnectionFailure(e: Exception)
}

object FileGroupJobHandlerFSM {
  sealed trait State
  case object NotConnected extends State
  case object Ready extends State

  sealed trait Data
  case object Working extends Data
  case class ConnectionFailed(e: Throwable) extends Data
}

class FileGroupJobHandler extends Actor with FSM[State, Data] {
  this: MessageServiceComponent =>

  import FileGroupJobHandlerProtocol._

  private val ReconnectionInterval = 1 seconds

  startWith(NotConnected, Working)

  when(NotConnected) {
    case Event(ListenForFileGroupJobs, _) => {
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => goto(Ready)
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", ListenForFileGroupJobs, ReconnectionInterval, repeat = false)
          stay using ConnectionFailed(e)
        }
      }
    }
    case Event(ConnectionFailure(e), _) => stay
  }

  when(Ready) {
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
  }

  onTransition {
    case _ -> NotConnected => (nextStateData: @unchecked) match { // error if ConnectionFailed is not set
      case ConnectionFailed(e) => self ! ListenForFileGroupJobs
    }
  }

  initialize

  private def deliverMessage(message: String): Future[Unit] = {
    import context.dispatcher
    future {
      ()
    }
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }
}