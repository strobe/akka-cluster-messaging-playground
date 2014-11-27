package cc.evgeniy.akka.messaging.actors

import java.util.concurrent.TimeUnit
import akka.actor.{ Actor, ActorLogging, Props}
import akka.routing.FromConfig
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration
import cc.evgeniy.akka.messaging.utils.SystemAddressExtension


class NodeWorkerActor extends Actor with ActorLogging {

  val system = context.system

  implicit def executionContext: ExecutionContextExecutor =
    context.system.dispatchers.lookup("my-fork-join-dispatcher")

  var startedTime = System.currentTimeMillis()

  // router which defined in config
  val router = context.actorOf(FromConfig.props(Props.empty)
    .withDispatcher("my-fork-join-dispatcher"), name= "nodesRouter")

  var schedule =
    system.scheduler.schedule(Duration.Zero,
                              interval= Duration(30, TimeUnit.MILLISECONDS)) {
      sendCommonMessage()
    }

  var countQueue = mutable.Queue[Long]()


  def receive = {
    case SetSendTimeout(ms: Int) => {
      changeSendTimeout(ms)
      log.info(s"timeout changed $ms")
    }

    case CommonMessage => doMessagesLogStats()
    case _             => // ignore
  }


  def changeSendTimeout(ms: Int) = {
    schedule.cancel()
    schedule =
      system.scheduler.schedule(Duration.Zero,
                                interval= Duration(ms, TimeUnit.MILLISECONDS)) {
        sendCommonMessage()
      }
  }


  def sendCommonMessage () = {
    router ! CommonMessage
  }

  def doMessagesLogStats() = {
    val now = System.currentTimeMillis()
    val lastSec = now - 1000

    // removing all elements which is lived more that second
    countQueue.dequeueAll(_ <= lastSec)

    // add current timestamp
    countQueue += now

    // count of messages for last second
    val count = countQueue.length

    log.info(s"${SystemAddressExtension(context.system).address.toString} " +
      s" $count Messages processed in 1 second")
  }
}
