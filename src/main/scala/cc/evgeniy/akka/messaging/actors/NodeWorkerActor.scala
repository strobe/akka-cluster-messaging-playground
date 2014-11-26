package cc.evgeniy.akka.messaging.actors

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorLogging, Props}
import akka.routing.FromConfig
import cc.evgeniy.akka.messaging.utils.SystemAddressExtension

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration


class NodeWorkerActor extends Actor with ActorLogging {

  implicit def executionContext: ExecutionContextExecutor = context.system.dispatchers.lookup("my-fork-join-dispatcher")

  var startedTime = System.currentTimeMillis()
  var count = 0

  // router which defined in config
  val router = context.actorOf(FromConfig.props(Props.empty).withDispatcher("my-fork-join-dispatcher"), name = "nodesRouter")

  var schedule = context.system.scheduler.schedule(Duration.Zero,
                                                   interval=Duration(5, TimeUnit.MILLISECONDS)) {
    sendCommonMessage()
  }


  override def preStart(): Unit = {
    //log.info(s"router started ${router.path}")
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

  def doLogStats() = {
    count += 1
    val now = System.currentTimeMillis()
    val seconds = (now - startedTime)/1000
    if (seconds >= 1) {
      startedTime = System.currentTimeMillis()
      count = 0
    }
    log.info(s"${SystemAddressExtension(context.system).address.toString}  $count Messages processed in 1 second")
  }
}
