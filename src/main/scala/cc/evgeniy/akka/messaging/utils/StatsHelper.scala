package cc.evgeniy.akka.messaging.utils

import akka.actor._
import scala.concurrent.ExecutionContext
import cc.evgeniy.akka.messaging.actors.{CurrentStats, GetStats, askForStats}

class StatsHelper(router: ActorRef, actorRefFactory: ActorSystem)(implicit ec: ExecutionContext) {
  def makeStatsActor: ActorRef = actorRefFactory.actorOf {
    Props {
      new Actor with ActorLogging {
        var replyTo: ActorRef = sender()

        def receive = {
          case askForStats(nodes: Int) => {
            replyTo = sender()
            router ! GetStats(nodes, self)
          }

          case CurrentStats(nodes: Int, processedMessages: Int, timeout: Int) => {
            replyTo ! Some((nodes, processedMessages, timeout))
          }

          case _ => // ignore
        }

      }
    }
  }
}
