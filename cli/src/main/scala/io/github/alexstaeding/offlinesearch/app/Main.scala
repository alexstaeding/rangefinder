package io.github.alexstaeding.offlinesearch.app

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.*
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.parasitic
import scala.io.StdIn
import scala.util.{Failure, Random, Success}

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

case class AppDT(data: String)

object AppDT {
  given codec: JsonValueCodec[AppDT] = JsonCodecMaker.make
  given hashingAlgorithm: HashingAlgorithm[AppDT] = (value: AppDT) => {
    val hash = value.data.hashCode
    NodeId(Array[Byte]((hash >> 24).toByte, (hash >> 16).toByte, (hash >> 8).toByte, hash.toByte))
  }
}

@main
def hello(): Unit = {
  val x = Random.nextInt(10)
  logger.info(s"Starting client $x")
  val localNodeId = NodeId.generateRandom
  val bindAddress = InetSocketAddress("localhost", 9000 + x)
  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  logger.info("Type ping(id) to send a ping to a node")
  val routing = new KademliaRouting[AppDT](HttpNetworkAdapter, localNodeInfo)
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
          .store(AppDT(value))
          .onComplete {
            case Success(value) =>
              logger.info(s"Stored value: $value")
            case Failure(exception) =>
              logger.error(s"Failed to store value", exception)
          }(using ExecutionContext.parasitic)
      case s"findValue($id)" =>
        logger.info(s"Finding value for $id")
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            routing
              .findValue(nodeId)
              .onComplete {
                case Success(value) =>
                  logger.info(s"Found value: $value")
                case Failure(exception) =>
                  logger.error(s"Failed to find value", exception)
              }(using ExecutionContext.parasitic)
          case None =>
            logger.info(s"Invalid node id: '$id'")
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
