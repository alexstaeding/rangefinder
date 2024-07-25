package io.github.alexstaeding.offlinesearch.headless

import io.github.alexstaeding.offlinesearch.network.*
import io.github.alexstaeding.offlinesearch.types.simple.StringIndex
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import scala.util.CommandLineParser

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

given parser: CommandLineParser.FromString[NodeId] with
  override def fromString(s: String): NodeId = NodeId.fromHex(s).getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $s") }
  override def fromStringOption(s: String): Option[NodeId] = NodeId.fromHex(s)

@main
def headlessMain(): Unit = {
  logger.info(s"Starting headless node")
  val bindAddress = InetSocketAddress(9400)
  val localNodeId = Option(System.getenv("NODE_ID")) match
    case Some(value) =>
      NodeId
        .fromHex(value)
        .getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $value") }
    case None => throw new IllegalArgumentException("NODE_ID environment variable is required")

  val localNodeInfo = NodeInfo(localNodeId, bindAddress)

  val observerAddress = Option(System.getenv("OBSERVER_ADDRESS")) match
    case Some(s"$host:$port") => Some(InetSocketAddress(host, port.toInt))
    case Some(value)          => throw new IllegalArgumentException(s"Invalid observer address: '$value', must be in the form 'host:port'")
    case None                 => None

  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  // start server threads
  val content = StringIndex.getContent(localNodeId)
  logger.info(s"Local content keys: ${content.keys.mkString(", ")}")
  val contentBrowser = new ContentBrowser(InetSocketAddress(8080), content)
  val routing = new KademliaRouting[StringIndex](HttpNetworkAdapter, localNodeInfo, observerAddress)
}
