package io.github.alexstaeding.rangefinder.operator

import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.alexstaeding.rangefinder.network.NodeId
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util

class Operator(
    private val networkListener: NetworkListener,
    private val bindAddress: InetSocketAddress,
)(using logger: Logger) {

  private val client = new KubernetesClientBuilder().build()
  private val actions = new OperatorActions(client, logger, idSpace)
  private val random = new util.Random

  logger.info(s"Connected to Kubernetes API server ${client.getMasterUrl} with API version ${client.getApiVersion}.")
  logger.info(s"In namespace ${client.getNamespace}.")

  networkListener.register(
    bindAddress,
    new EventReceiver {
      override def receiveInit(): Seq[String] =
        try init()
        catch
          case e: Exception =>
            logger.error("Failed to create node", e)
            Seq.empty
      override def receiveClean(): Boolean =
        try
          actions.clean()
          true
        catch
          case e: Exception =>
            logger.error(s"Failed to remove nodes", e)
            false
    },
  )

  private def init(): Seq[String] = {
    ((1 to 20).map { _ => ("headless", createNode("headless")) } :+ ("cli", createNode("cli"))).map { (nodeType, nodeId) =>
      s"${nodeId.toHex},$nodeType-${nodeId.toHex},9400"
    }
  }

  private def createNode(nodeType: String): NodeId = {
    val nodeId = NodeId.generateRandom(random)
    val visualizerUrl = Option(System.getenv("VISUALIZER_URL"))
    logger.info(s"Creating node with id $nodeId and visualizer url $visualizerUrl")
    actions.createNode(nodeId, nodeType, visualizerUrl)
    nodeId
  }
}
