package io.github.alexstaeding.offlinesearch.cli

import io.github.alexstaeding.offlinesearch.meta.*
import io.github.alexstaeding.offlinesearch.network.*
import io.github.alexstaeding.offlinesearch.types.simple.StringIndex
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
  val localNodeId = NodeId.generateRandom(Some(clientNum))
  val bindAddress = InetSocketAddress("localhost", 9000 + clientNum)
  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
//  val observerAddress = Some(InetSocketAddress("localhost", 3000))
  val observerAddress = None
  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  logger.info("Type ping(id) to send a ping to a node")
  // start server threads
  val routing = new KademliaRouting[StringIndex](HttpNetworkAdapter, localNodeInfo, observerAddress)
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
        logger.info(s"Storing value: $value")
        routing
          .store(OwnedValue(localNodeId, StringIndex(value), "test"))
          .onComplete {
            case Success(value) =>
              logger.info(s"Stored value: $value")
            case Failure(exception) =>
              logger.error(s"Failed to store value", exception)
          }(using ExecutionContext.parasitic)
      case s"search($search)" =>
        logger.info(s"Search for $search")
        routing
          .search(PartialKey.ofOne(StringIndex(search)))
          .onComplete {
            case Success(value) =>
              logger.info(s"Found value: $value")
            case Failure(exception) =>
              logger.error(s"Failed to find value", exception)
          }(using ExecutionContext.parasitic)
      case s"putLocalNode($id,$host,$port)" =>
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            logger.info(s"putLocalNode $nodeId")
            routing.putLocalNode(nodeId, NodeInfo(nodeId, InetSocketAddress(host, port.toInt)))
          case None =>
            logger.info(s"Invalid input, should be nodeId,host,port: '$line'")
      case _ =>
        logger.warn("Unknown command")
    }
  }
}
