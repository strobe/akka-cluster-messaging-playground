package cc.evgeniy.akka.messaging


object TestingApp {
  def main(args: Array[String]): Unit = {
    val ports = Array("2551", "2552", "0", "0", "0")
    ClusterMessagingApp.main(ports)
  }
}
