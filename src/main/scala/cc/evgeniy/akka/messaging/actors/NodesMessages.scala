package cc.evgeniy.akka.messaging.actors

import akka.actor.ActorRef

//#messages

// send messages
case class CommonMessage()
case class SetSendTimeout(ms: Int)

case class askForStats(nodes: Int)

// ask messages
case class GetStats(nodes: Int, master: ActorRef)

// responses
case class CurrentStats(nodes: Int, processedMessages: Int, timeout: Int)

//#messages