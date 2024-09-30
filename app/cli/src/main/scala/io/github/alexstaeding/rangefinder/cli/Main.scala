package io.github.alexstaeding.rangefinder.cli

import io.github.alexstaeding.rangefinder.meta.*
import io.github.alexstaeding.rangefinder.network.*
import io.github.alexstaeding.rangefinder.types.simple.{StringIndex, StringPayload}
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.parasitic
import scala.io.StdIn
import scala.util.{CommandLineParser, Failure, Success}

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

@main
def cliMain(clientNum: Int): Unit = {
  logger.info(s"Starting client $clientNum")
  val localNodeId = NodeId.generateRandom(clientNum)

  val host = Option(System.getenv("HOST")) match
    case Some(value) => value
    case None        => "localhost"

  val bindAddress = Option(System.getenv("P2P_PORT")).flatMap(_.toIntOption) match
    case Some(port) => InetSocketAddress(host, port)
    case None => InetSocketAddress(host, 9000 + clientNum)

  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
//  val observerAddress = Some(InetSocketAddress("localhost", 3000))
  val observerAddress = None
  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  logger.info("Type ping(id) to send a ping to a node")
  // start server threads
  val routing = new KademliaRouting[StringIndex, StringPayload](HttpNetworkAdapter, localNodeInfo, observerAddress)
  while (true) {
    val line = StdIn.readLine()
    line match {
      case s"ping($id)" =>
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            logger.info(s"Sending ping to $nodeId")
            routing
              .ping(nodeId)
              .onComplete {
                case Success(value) =>
                  logger.info(s"Received ping response from $nodeId: $value")
                case Failure(exception) =>
                  logger.error(s"Failed to ping $nodeId", exception)
              }(using ExecutionContext.parasitic)
          case None =>
            logger.info(s"Invalid node id: '$id'")
      case s"store($value)" =>
        logger.info(s"Storing entry: $value")
        routing
          .store(IndexEntry.Value(localNodeId, StringIndex(value), StringPayload("test")))
          .onComplete {
            case Success(value) =>
//              logger.info(s"Stored entry: $value")
            case Failure(exception) =>
              logger.error(s"Failed to store entry", exception)
          }(using ExecutionContext.parasitic)
      case s"search($search)" =>
        logger.info(s"Search for $search")
        routing
          .searchWithPath(PartialKey.ofString(search).map(StringIndex(_)))
          .onComplete {
            case Success(entries) =>
              entries.foreach { case (entry, path) =>
                logger.info(
                  "Found via " + LazyList
                    .unfold(Option(path)) {
                      case Some(NodePath(node, prev)) => Some((node, prev))
                      case None                       => None
                    }
                    .map(_.id)
                    .mkString(" -> "),
                )
                logger.info(s"Result: $entry")
              }
            case Failure(exception) =>
              logger.error(s"Failed to find entry", exception)
          }(using ExecutionContext.parasitic)
      case s"putLocalNode($id,$host,$port)" =>
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            logger.info(s"putLocalNode $nodeId")
            routing.putLocalNode(NodeInfo(nodeId, InetSocketAddress(host, port.toInt)))
          case None =>
            logger.info(s"Invalid input, should be nodeId,host,port: '$line'")
      case _ =>
        logger.warn("Unknown command")
    }
  }
}
