package cc.evgeniy.akka.messaging.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.routing.FromConfig

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration


class NodeWorkerActor extends Actor with ActorLogging {

  implicit def executionContext: ExecutionContextExecutor = context.dispatcher

  val cluster = Cluster(context.system)

  // router which defined in config
  val router = context.actorOf(FromConfig.props(Props.empty), name = "nodesRouter")

  override def preStart(): Unit = {
    context.system.scheduler.schedule(Duration.Zero, interval=Duration(100, TimeUnit.MILLISECONDS)) {
      router ! CommonMessage
    }
    log.info(s"router started ${router.path}")
  }

  override def postStop(): Unit = {}

  def receive = {
    case CommonMessage => doLogStats()
    case _ => // ignore
  }


  def computeStats() = {
    "empty now"
  }

  def doLogStats() = {
    log.info(s"common message received to ${self.path}.\n - stats: ${computeStats()}")
  }
}
