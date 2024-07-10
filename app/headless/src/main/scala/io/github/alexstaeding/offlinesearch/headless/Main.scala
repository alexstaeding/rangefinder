package io.github.alexstaeding.offlinesearch.headless

import io.github.alexstaeding.offlinesearch.network.*
import io.github.alexstaeding.offlinesearch.types.simple.StringIndex
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import scala.util.CommandLineParser

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

@main
def headlessMain(nodeNum: Int, rest: String*): Unit = {
  logger.info(s"Starting headless node")
  val localNodeId = NodeId.generateRandom(Some(nodeNum))
  val bindAddress = InetSocketAddress(9400)
  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
  val observerAddress = rest match
    case s"$host:$port" :: xs => Some(InetSocketAddress(host, port.toInt))
    case _                    => None

  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  // start server threads
  val routing = new KademliaRouting[StringIndex](HttpNetworkAdapter, localNodeInfo, observerAddress)
}
