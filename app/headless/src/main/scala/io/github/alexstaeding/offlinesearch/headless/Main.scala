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

private def requirePort(envName: String): InetSocketAddress =
  Option(System.getenv(envName)).flatMap(_.toIntOption) match
    case Some(port) => InetSocketAddress(port)
    case None       => throw new IllegalArgumentException(s"$envName environment variable is required")
@main
def headlessMain(): Unit = {

  val existingNode = Option(System.getenv("BUDDY_NODE_ID")).map { case s"$id:$host:$port" =>
    NodeInfo(
      id = NodeId.fromHex(id).getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $id") },
      address = InetSocketAddress(host, port.toInt),
    )
  }

  val p2pAddress = requirePort("P2P_PORT")
  val contentAddress = requirePort("CONTENT_PORT")

  val localNodeId = Option(System.getenv("NODE_ID")) match
    case Some(value) =>
      NodeId
        .fromHex(value)
        .getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $value") }
    case None => throw new IllegalArgumentException("NODE_ID environment variable is required")

  val localNodeInfo = NodeInfo(localNodeId, p2pAddress)

  val observerAddress = Option(System.getenv("OBSERVER_ADDRESS")) match
    case Some(s"$host:$port") => Some(InetSocketAddress(host, port.toInt))
    case Some(value)          => throw new IllegalArgumentException(s"Invalid observer address: '$value', must be in the form 'host:port'")
    case None                 => None

  logger.info(s"Starting headless node with buddy ${existingNode.map(_.toString).getOrElse("None")}" +
    s" and observer address ${observerAddress.map(_.toString).getOrElse("None")}")

  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $p2pAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${p2pAddress.getHostString},${p2pAddress.getPort}")
  // start server threads
  val content = StringIndex.getContent(localNodeId)
  logger.info(s"Local content keys: ${content.keys.mkString(", ")}")
  val contentBrowser = new ContentBrowser(contentAddress, content)
  val routing = new KademliaRouting[StringIndex](HttpNetworkAdapter, localNodeInfo, observerAddress)

  existingNode.foreach { node => routing.putLocalNode(node.id, node) }

  while (true) {
    val targetRandomId = NodeId.generateRandom()
    logger.info(s"Sending out random ping to ${targetRandomId.toHex}")
    routing.ping(targetRandomId)
    Thread.sleep(10000)
  }
}
