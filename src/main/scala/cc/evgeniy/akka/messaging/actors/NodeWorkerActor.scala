package cc.evgeniy.akka.messaging.actors

import java.util.concurrent.TimeUnit
import akka.actor.{ActorRef, Actor, ActorLogging, Props}
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

  var timeout = 100

  var countQueue = mutable.Queue[Long]()

  var schedule =
    system.scheduler.schedule(Duration.Zero,
                              interval= Duration(timeout, TimeUnit.MILLISECONDS)) {
      sendCommonMessage()

    }

  def receive = {
    case SetSendTimeout(ms: Int) => {
      changeSendTimeout(ms)
      log.info(s"timeout changed $ms")
    }

    case GetStats(nodes: Int, master: ActorRef) => {
      master ! CurrentStats(nodes, countOfProcessedMessages(), timeout)
    }

    case CommonMessage => doMessagesLogStats()
    case _             => // ignore
  }


  def changeSendTimeout(ms: Int) = {
    schedule.cancel()
    timeout  = ms
    schedule =
      system.scheduler.schedule(Duration.Zero,
                                interval= Duration(ms, TimeUnit.MILLISECONDS)) {
        sendCommonMessage()
      }
  }


  def sendCommonMessage () = {
    router ! CommonMessage
  }


  def countOfProcessedMessages(): Int = {
    val now = System.currentTimeMillis()
    val lastSec = now - 1000

    // removing all elements which is lived more that second
    countQueue.dequeueAll(_ <= lastSec)

    // count of messages for last second
    val count: Int = countQueue.length
    count
  }


  def doMessagesLogStats() = {
    val now = System.currentTimeMillis()

    // add current timestamp
    countQueue += now

    log.info(s"${SystemAddressExtension(context.system).address.toString} " +
      s" ${countOfProcessedMessages()} Messages processed in 1 second")
  }
}
