package io.github.alexstaeding.rangefinder.operator

import io.github.alexstaeding.rangefinder.network.NodeIdSpace
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

@main
def operatorMain(): Unit = {
  logger.info("Starting operator...")
  val operator = new Operator(HttpNetworkListener, new InetSocketAddress(80))
}
