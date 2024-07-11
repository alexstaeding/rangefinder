package io.github.alexstaeding.offlinesearch.operator

import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.alexstaeding.offlinesearch.network.NodeId
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util

class Operator(
    private val networkListener: NetworkListener,
    private val bindAddress: InetSocketAddress,
)(using logger: Logger) {

  private val client = new KubernetesClientBuilder().build()
  private val actions = new OperatorActions[String](client)
  private val random = new util.Random

  logger.info(s"Connected to Kubernetes API server ${client.getMasterUrl} with API version ${client.getApiVersion}.")
  logger.info(s"In namespace ${client.getNamespace}.")

  networkListener.register(
    bindAddress,
    new EventReceiver {
      override def receiveAddNode(): Boolean =
        try createNode()
        catch
          case e: Exception =>
            logger.error("Failed to create node", e)
            false
      override def receiveRemoveNode(id: NodeId): Boolean =
        try actions.removeNode(id)
        catch
          case e: Exception =>
            logger.error(s"Failed to remove node $id", e)
            false
    },
  )

  private def createNode(): Boolean = {
    val nodeId = NodeId.generateRandom(random)
    logger.info(s"Creating node with id $nodeId")
    actions.createNode(nodeId)
  }
}
