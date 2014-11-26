package cc.evgeniy.akka.messaging.utils

import akka.actor.{ExtensionKey, Extension, ExtendedActorSystem}

class SystemAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
  def address = system.provider.getDefaultAddress
}

object SystemAddressExtension extends ExtensionKey[SystemAddressExtensionImpl]