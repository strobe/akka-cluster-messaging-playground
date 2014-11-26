package cc.evgeniy.akka.messaging.actors

import akka.actor._
import akka.routing.FromConfig
import scala.concurrent.ExecutionContextExecutor


class MasterActor extends Actor with ActorLogging {

  implicit def executionContext: ExecutionContextExecutor = context.dispatcher

  // router which defined in config
  val router = context.actorOf(FromConfig.props(Props.empty), name = "masterNodesRouter")

  def receive = {
    case SetSendTimeout(ms: Int) => {
      router ! SetSendTimeout(ms)
    }

    case _ => // ignore
  }

}
