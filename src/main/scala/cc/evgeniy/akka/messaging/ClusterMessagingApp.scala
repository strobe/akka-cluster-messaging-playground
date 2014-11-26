package cc.evgeniy.akka.messaging

import akka.actor._
import com.typesafe.config.ConfigFactory

import cc.evgeniy.akka.messaging.actors._
import cc.evgeniy.akka.messaging.utils.SystemAddressExtension

import scala.concurrent.ExecutionContextExecutor


object ClusterMessagingApp {

  val defaultConfig = ConfigFactory.load()

  // Override the configuration of the port
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + 2551)
    .withFallback(ConfigFactory.parseString("akka.cluster.roles = [master]"))
    .withFallback(defaultConfig)

  // Create an Akka system
  val system = ActorSystem("ClusterSystem", config)

  implicit def executionContext: ExecutionContextExecutor = system.dispatchers.lookup("my-fork-join-dispatcher")

  // master actor
  val master = system.actorOf(Props[MasterActor], name = "master")

  var workerSystems = Vector.empty[ActorSystem]

  // Create an actor that handles cluster domain events
  system.actorOf(Props[SimpleClusterListener], name = "clusterListener")


  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2552", "0", "0", "0"))
    else
      startup(args)

    val TimeoutPattern = "(set_timeout \\d+)".r  // '\\d' - digits, '+' 1 or more
    val AddNodePattern = "(add_node \\d+)".r  // '\\d' - digits, '+' 1 or more

    def commandLoop(): Unit = {
      scala.io.StdIn.readLine(">> ") match {

        case "help" => println("'quit' - exit")

        case TimeoutPattern(s) => {
          val timeout = "(\\d+)".r findFirstIn s // getting digits from string

          timeout match {
            case Some(t) if t.toInt > 0 => {
              // send
              master ! SetSendTimeout(t.toInt)
              println(s"SetTimeout message sent with: $t ms")
            }
            case _ => println("incorrect input")
          }
        }

        case AddNodePattern(s) => {
          val count = "(\\d+)".r findFirstIn s // getting digits from string
          count match  {
            case Some(n) if n.toInt > 0 => {
              addNode(n.toInt, "0", executionContext)
            }
            case _ =>  println("incorrect input")
          }
        }

        case "add_node" => addNode(1, "0", executionContext)

        case "remove_node" => removeNode()

        case "list_nodes" => listNodes()

        case "get_stats" => ???

        case "quit"          => return

        case _               => // ignore
      }
      commandLoop()
    }
    commandLoop()

    // finish all
    shutdown()


  }


  def removeNode() = {
    val candidate = workerSystems.last
    workerSystems = workerSystems filterNot candidate.==
    candidate.shutdown()
    // TODO: it should wait for cluster MemberRemoved message before say that is removed
    println("node removed")
  }


  def listNodes() = {
    s""" Nodes:\n ${
      for { n <- workerSystems } yield println(SystemAddressExtension(n).address.toString)
    }"""
  }


  /**
   * TOOD: some code here should launch new cloud VM instances and JVMs with our cluster worker app
   */
  def addNode(count: Int, port: String,  executionContext: ExecutionContextExecutor) = {
    for(x <- 1 to count ) {
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [node]"))
        .withFallback(defaultConfig)

      // Create an Akka system
//      val system = ActorSystem("ClusterSystem", config)
      val system = ActorSystem("ClusterSystem", config = Some(config), defaultExecutionContext=Some(executionContext))

      // node worker actor
      val worker = system.actorOf(Props[NodeWorkerActor], name = s"worker")

     workerSystems = workerSystems :+ system

      // TODO: it should wait for cluster MemberUP message before say that is added
      println(s"node $worker has been added")
    }
  }


  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      addNode(1, port, executionContext)
    }
  }

  def shutdown() = {
    for { n <- workerSystems } yield n.shutdown()
    system.shutdown()
  }
}






