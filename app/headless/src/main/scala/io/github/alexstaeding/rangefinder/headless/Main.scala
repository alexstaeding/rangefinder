package io.github.alexstaeding.rangefinder.headless

import io.github.alexstaeding.rangefinder.network.*
import io.github.alexstaeding.rangefinder.types.simple.{StringIndex, StringPayload}
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import scala.util.CommandLineParser

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

given parser: CommandLineParser.FromString[NodeId] with
  override def fromString(s: String): NodeId = NodeId.fromHex(s).getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $s") }
  override def fromStringOption(s: String): Option[NodeId] = NodeId.fromHex(s)

private def requirePort(host: String, envName: String): InetSocketAddress =
  Option(System.getenv(envName)).flatMap(_.toIntOption) match
    case Some(port) => InetSocketAddress(host, port)
    case None       => throw new IllegalArgumentException(s"$envName environment variable is required")

@main
def headlessMain(): Unit = {

  val host = Option(System.getenv("HOST")) match
    case Some(value) => value
    case None        => throw new IllegalArgumentException("HOST environment variable is required")

  val existingNode = Option(System.getenv("BUDDY_NODE")).map { case s"$id:$host:$port" =>
    NodeInfo(
      id = NodeId.fromHex(id).getOrElse { throw new IllegalArgumentException(s"Invalid NodeId: $id") },
      address = InetSocketAddress(host, port.toInt),
    )
  }

  val p2pAddress = requirePort(host, "P2P_PORT")
  val contentAddress = requirePort(host, "CONTENT_PORT")

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

  val externalContentUrl = Option(System.getenv("EXTERNAL_CONTENT_URL")).map(_ + localNodeId.toHex)

  logger.info(
    s"Starting headless node with buddy ${existingNode.map(_.toString).getOrElse("None")}" +
      s" and observer address ${observerAddress.map(_.toString).getOrElse("None")}",
  )

  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $p2pAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${p2pAddress.getHostString},${p2pAddress.getPort}")
  // start server threads
  val content = StringIndex.getContent(localNodeId)
  logger.info(s"Local content keys: ${content.keys.mkString(", ")}")
  val contentBrowser = new ContentBrowser(contentAddress, content)
  val router = new KademliaRouter[StringIndex, StringPayload](
    HttpNetworkAdapter,
    localNodeInfo,
    observerAddress,
    externalContentUrl,
    Some(content.keys.toSeq),
  )

  existingNode.foreach(router.putLocalNode)

  Thread.sleep(5000)

  while (true) {
    val targetRandomId = NodeId.generateRandom()
    logger.info(s"Sending out random find to ${targetRandomId.toHex}")
    router.findNode(targetRandomId)

    content.foreach((path, document) =>
      document
        .filter(x => x.isLetterOrDigit || x.isSpaceChar)
        .split("\\W+")
        .groupBy(identity)
        .view
        .mapValues(_.length)
        .foreach { (word, frequency) =>
          if (word.length > 2) {
            router.store(IndexEntry.Value(localNodeId, StringIndex(word), StringPayload(path)))
          }
        },
    )

    Thread.sleep(20000)
  }
}
