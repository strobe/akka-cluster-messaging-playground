package cc.evgeniy.akka.messaging

import akka.actor._
import com.typesafe.config.ConfigFactory

import cc.evgeniy.akka.messaging.actors._


object ClusterMessagingApp {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0", "0", "0"))
    else
      startup(args)

    /*
    def commandLoop(): Unit = {
      scala.io.StdIn.readLine(">> ") match {
        case "help"          => println("'quit' - exit")
        case "quit"          => return
        case _               => // ignore
      }

      commandLoop()
    }

    commandLoop()
    */
  }


  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [node]"))
        .withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)

      // Create an actor that handles cluster domain events
      system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

      // node worker actor
      system.actorOf(Props[NodeWorkerActor], name = "worker")
    }
  }
}
