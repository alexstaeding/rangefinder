package io.github.alexstaeding.offlinesearch.network
import java.util
import scala.collection.mutable
import scala.concurrent.Future

class KademliaRouting[V](
    private val idSpace: NodeIdSpace,
    private val localNodeId: NodeId,
    private val kSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using
    private val env: {
      val network: Network
    },
) extends Routing[V] {

  private val buckets: mutable.Seq[KBucket] = new util.ArrayList[]()
  private val homeBucket: KBucket = new KBucket

  private def bucketIndex(id: NodeId): Int = {
    import KademliaRouting.xor
    val distance = localNodeId.xor(id)
    val firstByte = distance.bytes.indexWhere(_ != 0)
    val bitPrefix = Integer.numberOfLeadingZeros(distance.bytes(firstByte)) - 24
    firstByte * 8 + bitPrefix
  }

  class KBucket {
    val nodes: mutable.Seq[NodeInfo] = new mutable.ArrayDeque[NodeInfo]
    val values: mutable.Seq[V] = new mutable.ArrayDeque[V]
  }

  override def ping(id: NodeId): Future[Unit] = {}

  override def store(id: NodeId, value: V): Future[Boolean] = ???

  override def findNode(id: NodeId): Future[NodeInfo] = ???

  override def findValue(id: NodeId): Future[V] = ???
}

object KademliaRouting {
  extension (self: NodeId) {
    def xor(other: NodeId): NodeId = {
      require(self.space == other.space, "Node IDs must be of the same length")
      NodeId(self.bytes.zip(other.bytes).map((x, y) => (x ^ y).toByte))(using self.space)
    }
  }
}
