package io.github.alexstaeding.offlinesearch.operator

import io.github.alexstaeding.offlinesearch.network.NodeIdSpace
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

@main
def operatorMain(): Unit = {
  println("Foo")
  logger.info("Starting operator...")
  val operator = new Operator(HttpNetworkListener, new InetSocketAddress(80))
}
