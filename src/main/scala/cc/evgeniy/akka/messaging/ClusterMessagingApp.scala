package cc.evgeniy.akka.messaging

import akka.actor._
import com.typesafe.config.ConfigFactory

import cc.evgeniy.akka.messaging.actors._
import cc.evgeniy.akka.messaging.utils.SystemAddressExtension



object ClusterMessagingApp {

  // Override the configuration of the port
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + 2551)
    .withFallback(ConfigFactory.parseString("akka.cluster.roles = [master]"))
    .withFallback(ConfigFactory.load())

  // Create an Akka system
  val system = ActorSystem("ClusterSystem", config)

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

    def commandLoop(): Unit = {
      scala.io.StdIn.readLine(">> ") match {

        case "help" => println("'quit' - exit")

        case TimeoutPattern(c) => {
          val timeout = "(\\d+)".r findFirstIn c // getting digits from string

          timeout match {
            case Some(t) if t.toInt > 0 => {

              // send
              master ! SetSendTimeout(t.toInt)

              println(s"SetTimeout message sent with: $t ms")
            }

            case _ => println("incorrect input")
          }
        }

        case "add_node" => addNode("0")

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


  def addNode(port: String) = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [node]"))
      .withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("ClusterSystem", config)

    // node worker actor
    val worker = system.actorOf(Props[NodeWorkerActor], name = "worker")

    workerSystems = workerSystems :+ system

    // TODO: it should wait for cluster MemberUP message before say that is added
    println(s"node $worker has been added")
  }


  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      addNode(port)
    }
  }

  def shutdown() = {
    for { n <- workerSystems } yield n.shutdown()
    system.shutdown()
  }
}






