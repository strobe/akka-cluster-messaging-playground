package cc.evgeniy.akka.messaging.actors

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorLogging, Props}
import akka.routing.FromConfig

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration


class NodeWorkerActor extends Actor with ActorLogging {

  implicit def executionContext: ExecutionContextExecutor = context.dispatcher

  // router which defined in config
  val router = context.actorOf(FromConfig.props(Props.empty), name = "nodesRouter")

  var schedule = context.system.scheduler.schedule(Duration.Zero,
                                                   interval=Duration(100, TimeUnit.MILLISECONDS)) {
    sendCommonMessage()
  }


  override def preStart(): Unit = {
    log.info(s"router started ${router.path}")
  }


  def receive = {

    case CommonMessage => doLogStats()

    case SetSendTimeout(ms: Int) => {
      log.info(s"timeout changed $ms")
      changeSendTimeout(ms)
    }

    case _ => // ignore
  }


  def changeSendTimeout(ms: Int) = {
    schedule.cancel()
    schedule = context.system.scheduler.schedule(Duration.Zero,
                                                 interval=Duration(ms, TimeUnit.MILLISECONDS)) {
      sendCommonMessage()
    }
  }


  def sendCommonMessage () = {
    router ! CommonMessage
  }


  def computeStats() = {
    "empty now"
  }


  def doLogStats() = {
//    log.info(s"common message received to ${self.path}.\n - stats: ${computeStats()}")
  }
}
