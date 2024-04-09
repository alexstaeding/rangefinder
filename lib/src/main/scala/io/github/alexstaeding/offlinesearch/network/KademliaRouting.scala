package io.github.alexstaeding.offlinesearch.network
import scala.collection.mutable
import scala.concurrent.Future

class KademliaRouting[V](
    private val localNodeId: NodeId,
)(using
    private val env: {
      val network: HttpNetwork
    },
) extends Routing[V] {

  private val buckets: mutable.Map[NodeId, Either[NodeInfo, V]] = new mutable.HashMap()
  val t = Array.fill(2)(List.empty)

  private def bucketIndex(id: NodeId): Int = {
    import KademliaRouting.xor
    val distance = localNodeId.xor(id)
    distance.bytes.map(Integer.numberOfLeadingZeros(_) - 24)
  }

  class KBucket {}

  override def ping(key: K): Future[Unit] = {}

  override def store e(key: K, value: V): Future[Boolean] = ???

  override def findNode(key: K): Future[NodeInfo] = ???

  override def findValue(key: K): Future[V] = ???
}

object KademliaRouting {
  extension (self: NodeId) {
    def xor(other: NodeId): NodeId = {
      require(self.bytes.length == other.bytes.length, "Node IDs must be of the same length")
      NodeId(self.bytes.zip(other.bytes).map((x, y) => (x ^ y).toByte))
    }
  }
}
