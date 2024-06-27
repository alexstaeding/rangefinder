package io.github.alexstaeding.offlinesearch.cli

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.meta.*
import io.github.alexstaeding.offlinesearch.network.*
import org.apache.logging.log4j.{LogManager, Logger}

import java.net.InetSocketAddress
import java.security.MessageDigest
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.parasitic
import scala.io.StdIn
import scala.util.{CommandLineParser, Failure, Success}

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

case class AppDT(data: String)

object AppDT {
  given codec: JsonValueCodec[AppDT] = JsonCodecMaker.make
  given hashingAlgorithm: HashingAlgorithm[AppDT] = (value: PartialKey[AppDT]) => {
    val hash = MessageDigest
      .getInstance("SHA1")
      .digest(value.startInclusive.data.getBytes("UTF-8") ++ value.endExclusive.data.getBytes("UTF-8"))
    logger.info("Hash: " + hash.mkString("Array(", ", ", ")"))
    NodeId(hash.take(idSpace.size))
  }
  given universe: PartialKeyUniverse[AppDT] with
    override def getRootKey(value: AppDT): PartialKey[AppDT] =
      StringPrefixPartialKeyUniverse.getRootKey(value.data).map(AppDT.apply)
    override def getOverlappingRootKeys(key: PartialKey[AppDT]): Seq[PartialKey[AppDT]] =
      StringPrefixPartialKeyUniverse.getOverlappingRootKeys(key.map(_.data)).map(_.map(AppDT.apply))

  given matcher: PartialKeyMatcher[AppDT] with
    extension (partialKey: PartialKey[AppDT])
      override def matches(search: AppDT): Boolean = StringPrefixPartialKeyMatcher.matches(partialKey.map(_.data))(search.data)
  given ordering: Ordering[AppDT] = Ordering.by(_.data)
}

given CommandLineParser.FromString[Int] = Integer.parseInt(_)

@main
def main(clientNum: Int): Unit = {
  logger.info(s"Starting client $clientNum")
  val localNodeId = NodeId.generateRandom(Some(clientNum))
  val bindAddress = InetSocketAddress("localhost", 9000 + clientNum)
  val observerAddress = InetSocketAddress("localhost", 3000)
  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
  logger.info(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  logger.info(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  logger.info("Type ping(id) to send a ping to a node")
  val routing = new KademliaRouting[AppDT](HttpNetworkAdapter, localNodeInfo, observerAddress)
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
          .store(OwnedValue(localNodeId, AppDT(value), "test"))
          .onComplete {
            case Success(value) =>
              logger.info(s"Stored value: $value")
            case Failure(exception) =>
              logger.error(s"Failed to store value", exception)
          }(using ExecutionContext.parasitic)
      case s"search($search)" =>
        logger.info(s"Search for $search")
        routing
          .search(PartialKey.ofOne(AppDT(search)))
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
