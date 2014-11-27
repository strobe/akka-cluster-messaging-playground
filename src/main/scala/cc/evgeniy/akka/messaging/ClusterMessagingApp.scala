package cc.evgeniy.akka.messaging

import akka.actor._
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Await, ExecutionContextExecutor}
import cc.evgeniy.akka.messaging.actors._
import cc.evgeniy.akka.messaging.utils.SystemAddressExtension


object ClusterMessagingApp {

  val defaultConfig = ConfigFactory.load()

  // Override the configuration of the port
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port =" + 2551)
    .withFallback(ConfigFactory.parseString("akka.cluster.roles = [master]"))
    .withFallback(defaultConfig)

  // Create an Akka system
  val system = ActorSystem("ClusterSystem", config)

  // shared dispatcher for less intensive treads usage
  // (on production cluster should be individual dispatchers per node)
  implicit def executionContext: ExecutionContextExecutor =
    system.dispatchers.lookup("my-fork-join-dispatcher")

  // router which defined in config
  val router = system.actorOf(FromConfig.props(Props.empty)
    .withDispatcher("my-fork-join-dispatcher"), name= "masterNodesRouter")

  // Create an actor that handles cluster domain events
  system.actorOf(Props[SimpleClusterListener], name= "clusterListener")

  var workerSystems = Vector.empty[ActorSystem]


  def main(args: Array[String]): Unit = {
    // starting default nodes
    if (args.isEmpty) startup(Seq("2552", "0", "0", "0"))
    else              startup(args)

    val helpString ="""For messages stats see: log/akka.log
      #
      #Commands:
      #  help                  - this help text
      #  quit                  - exit from app
      #  nodes_count           - return cont of cluster nodes
      #  nodes_list            - show list of cluster nodes addresses
      #  set_timeout [ms]      - set messages sending timeout in milliseconds
      #  add_node              - adds single node
      #  add_nodes [count]      - adds multiply nodes
      #  remove_node           - remove single node
      #  remove_nodes [count]   - remove multiply nodes """.stripMargin('#')

    val timeoutPattern    = "(set_timeout \\d+)".r  // '\\d' - digits, '+' 1 or more
    val addNodePattern    = "(add_nodes \\d+)".r     // '\\d' - digits, '+' 1 or more
    val removeNodePattern = "(remove_nodes \\d+)".r  // '\\d' - digits, '+' 1 or more

    println(helpString)

    // infinity recursive loop for command line user interactions
    def commandLoop(): Unit = {
      scala.io.StdIn.readLine(">> ") match {
        // set_timeout $milliseconds
        case timeoutPattern(s) => {
          val timeout = "(\\d+)".r findFirstIn s // getting digits from string

          timeout match {
            case Some(t) if t.toInt > 0 => {
              // send
              router ! SetSendTimeout(t.toInt)
              println(s"SetTimeout message sent with: $t ms")
            }
            case _ => println("incorrect input")
          }
        }

        // add_node $count
        case addNodePattern(s)   => {
          val count = "(\\d+)".r findFirstIn s // getting digits from string
          count match  {
            case Some(n) if n.toInt > 0 => {
              addNode(n.toInt, "0", executionContext)
            }
            case _ =>  println("incorrect input")
          }
        }

        // remove_node $count
        case removeNodePattern(s) => {
          val count = "(\\d+)".r findFirstIn s // getting digits from string

          count match  {
            case Some(n) if n.toInt > 0 => {
              removeNode(n.toInt)
            }
            case _ =>  println("incorrect input")
          }
        }

        case "add_node"    => addNode(1, "0", executionContext)

        case "remove_node" => removeNode(1)

        case "nodes_list"  => nodesList()

        case "nodes_count" => nodesCount()

        case "quit"        => return

        case "help"        => println(helpString)

        case _             => // ignore
      }
      commandLoop()
    }
    commandLoop()

    // finish all
    shutdown()
  }


  // TODO: it should wait for cluster MemberRemoved message before say that is removed
  def removeNode(count: Int) = {
    if (count < workerSystems.length - 1) {
      for (x <- 1 to count) {
        // removing last node
        val candidate = workerSystems.last
        workerSystems = workerSystems filterNot candidate.==
        candidate.shutdown()

        println("node removed")
      }
    }
    else { println("cluster doesn't has enough nodes for this command") }
  }


  def nodesList() = {
    var nodesString = "Nodes:\\n"
    for(n <- workerSystems) {
      nodesString += SystemAddressExtension(n).address.toString + "\n"
    }

    println(nodesString)
  }


  def nodesCount() = {
    println(s"Nodes count: ${workerSystems.length}")
  }


  // TODO: some code here should launch new cloud VM instances and JVMs with our cluster worker app
  // This launched new ActorSystem as node which is really expansive, on
  // threads usage especially. Therefore as usual if about 100 nodes will be stared
  // (it will be about 1000 threads) is possible that we gonna catch many errors
  // related to threads, many files, sockets etc.
  def addNode(count: Int, port: String,  executionContext: ExecutionContextExecutor) = {
    for(x <- 1 to count ) {
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port = " + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [node]"))
        .withFallback(defaultConfig)

      // Create an Akka system
      val system = ActorSystem("ClusterSystem",
                               config= Some(config),
                               defaultExecutionContext= Some(executionContext)) // we use shared dispatcher here

      // node worker actor
      val worker = system.actorOf(Props[NodeWorkerActor], name= "worker")

      // saving node system reference
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
    System.exit(1)
  }
}






