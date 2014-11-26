package cc.evgeniy.akka.messaging.actors

//#messages
case class CommonMessage()
case class SetSendTimeout(ms: Int)

case class WorkerRegistration()
//#messages