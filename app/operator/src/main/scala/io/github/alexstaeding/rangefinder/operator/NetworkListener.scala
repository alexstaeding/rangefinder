package io.github.alexstaeding.rangefinder.operator

import io.github.alexstaeding.rangefinder.network.NodeId
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress

trait NetworkListener {
  def register(
      bindAddress: InetSocketAddress,
      onReceive: EventReceiver,
  )(using logger: Logger): Unit
}

trait EventReceiver {
  def receiveAddNode(): Boolean
  def receiveRemoveNode(id: NodeId): Boolean
}
