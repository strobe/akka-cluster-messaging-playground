package cc.evgeniy.akka.messaging

import akka.actor._
import akka.routing.FromConfig
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.collection.immutable.IndexedSeq
import scala.concurrent._
import scala.concurrent.duration._
import akka.pattern.ask

import cc.evgeniy.akka.messaging.utils.StatsHelper
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

  implicit val timeout = Timeout(10000.millisecond)

  // broadcast router which defined in config
  val router = system.actorOf(FromConfig.props(Props.empty)
    .withDispatcher("my-fork-join-dispatcher"), name= "masterNodesRouter")

  // random router which defined in config
  val randomRouter = system.actorOf(FromConfig.props(Props.empty)
    .withDispatcher("my-fork-join-dispatcher"), name= "masterRandomNodesRouter")

  // Create an actor that handles cluster domain events
  system.actorOf(Props[SimpleClusterListener], name= "clusterListener")

  var workerSystems = Vector.empty[ActorSystem]


  def main(args: Array[String]): Unit = {
    // starting default nodes
    if (args.isEmpty) startup(Seq("2552", "0", "0"))
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
      #  add_nodes [count]     - adds multiply nodes
      #  remove_node           - remove single node
      #  remove_nodes [count]  - remove multiply nodes
      #  get_stats [countOfNewNodesInStep] - returned analytics information and created specific count of nodes during it
      #  get_stats             - returned analytics information """.stripMargin('#')

    val timeoutPattern    = "(set_timeout \\d+)".r   // '\\d' - digits, '+' 1 or more
    val addNodePattern    = "(add_nodes \\d+)".r     // '\\d' - digits, '+' 1 or more
    val removeNodePattern = "(remove_nodes \\d+)".r  // '\\d' - digits, '+' 1 or more
    val getStatsPattern   = "(get_stats \\d+)".r     // '\\d' - digits, '+' 1 or more

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

        // get_stats $maxNodes
        case getStatsPattern(s) => {
          val count = "(\\d+)".r findFirstIn s // getting digits from string

          count match  {
            case Some(n) if n.toInt > 0 => {
              computeStats(n.toInt)
            }
            case _ =>  println("incorrect input")
          }
        }

        case "add_node"    => addNode(1, "0", executionContext)

        case "remove_node" => removeNode(1)

        case "nodes_list"  => nodesList()

        case "nodes_count" => println(s"Nodes count: ${nodesCount()}")

        case "get_stats"   => computeStats(1)

        case "quit"        => return

        case "help"        => println(helpString)

        case _             => println("unknown command")
      }
      commandLoop()
    }
    commandLoop()

    // finish all
    shutdown()
  }


  /**
   * nodes count -> 0 until 100 (upper limit sett by user)
   * messages in cluster/sec
   * messages per node/sec
   * sending timeout (ms) -> 0 until 100 by 5
   */
  def computeStats(addNodesStep: Int) = {

    println("please wait it will take a while ...")

    val initNodesCount      = nodesCount()
    val initTimeout         = getCurrentTimeout()
    val addNodesStep: Int   = 1
    val statsHelper =  new StatsHelper(randomRouter, system)
    var nodesAdded = 0

    val seq =
      for {
      // getting stats futures
        f <- (0 until 100 by 5) map( s => {
          // set timeout
          router ! (if (s > 0) SetSendTimeout(s) else SetSendTimeout(1))

          // add node
          addNode(addNodesStep, "0", executionContext, silent = true)
          nodesAdded += 1
          Thread.sleep(100)

          // getting stat from random node by new helper actor
          val actor = statsHelper.makeStatsActor
          val future = (actor ? askForStats(nodesCount()))
            .mapTo[Option[(Int, Int, Int)]].recover { case e => None}
          future
        })

        // getting stats values
        v: (Int, Int, Int) <- {
          Await.result(f, Duration.Inf)
        }
      } yield v


    // restore original state
    router ! SetSendTimeout(initTimeout)
    removeNode(nodesAdded)


    case class StatItem(nodes: Int, clusterMessages: Int, nodeMessages: Int, timeout: Int)

    val statItems = seq.map(x => {
      val clusterMessages = x._1.toInt * x._2.toInt
      StatItem(x._1, clusterMessages, x._2, x._3 )
    })

    // ugly pretty print TODO:
    println(s""" Roughly test stats:
            ||nodes count | messages in cluster/sec | messages per node/sec | sending timeout (ms) |
            |
            ${ (for {
      i <- statItems
    } yield s"|| ${i.nodes}      |" +
        s" ${i.clusterMessages}           |" +
        s" ${i.nodeMessages}              |" +
        s" ${i.timeout}                   |\n").mkString}
            |
            """.stripMargin)
  }


  def getCurrentTimeout() = {
    Await.result((router ? askForStats(nodesCount()))
      .mapTo[Option[(Int, Int)]]
      .recover { case e => None}, Duration.Inf) match {
      case Some(t) => t._1
      case None => 100 // default
    }
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
    var nodesString = "Nodes:\n"
    for(n <- workerSystems) {
      nodesString += SystemAddressExtension(n).address.toString + "\n"
    }

    println(nodesString)
  }


  def nodesCount() = {
    workerSystems.length
  }


  // TODO: some code here should launch new cloud VM instances and JVMs with our cluster worker app
  // This launched new ActorSystem as node which is really expensive, on
  // threads usage especially. Therefore as usual if about 100 nodes will be stared
  // (it will be about 1000 threads) is possible that we gonna catch many errors
  // related to threads, many files, sockets etc.
  def addNode(count: Int, port: String, executionContext: ExecutionContextExecutor,
              silent: Boolean = false) = {
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
      if (!silent) println(s"node $worker has been added")

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






