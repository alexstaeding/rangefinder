package io.github.alexstaeding.offlinesearch.network

import java.util
import scala.annotation.tailrec
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

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]()

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

  private def bucketIndex(id: NodeId): Int = {
    import KademliaRouting.xor
    val distance = localNodeId.xor(id)
    val firstByte = distance.bytes.indexWhere(_ != 0)
    val bitPrefix = Integer.numberOfLeadingZeros(distance.bytes(firstByte)) - 24
    firstByte * 8 + bitPrefix
  }

  private def moveEntries[T](
      partitionIndex: Int,
      source: mutable.Map[NodeId, T],
      destination: mutable.Map[NodeId, T],
  ): Unit =
    source
      .collect { case (key, _) if bucketIndex(key) == partitionIndex => key }
      .foreach { key => destination.put(key, source.remove(key).get) }

  @tailrec
  private def ensureBucketSpace(index: Int): Option[KBucket] = {
    if (buckets.size + 1 > index) {
      if (buckets.isEmpty) {
        Option.when(homeBucket.hasSpace)(homeBucket)
      } else {
        Option.when(buckets(index).hasSpace)(buckets(index))
      }
    } else {
      // index > buckets.size
      if (homeBucket.hasSpace) {
        Some(homeBucket)
      } else {
        val newTail = new KBucket
        moveEntries(buckets.size, homeBucket.nodes, newTail.nodes)
        moveEntries(buckets.size, homeBucket.values, newTail.values)
        buckets.addOne(newTail)
        ensureBucketSpace(index)
      }
    }
  }

  private def putInBucket(id: NodeId, value: NodeInfo): Boolean = {
    val index = bucketIndex(id)
    ensureBucketSpace(index) match {
      case Some(bucket) => {
        bucket.nodes.put(id, value)
        true
      }
      case None => false
    }
  }

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]()
    val values: mutable.Map[NodeId, V] = new mutable.HashMap[NodeId, V]()

    def isFull: Boolean = nodes.size + values.size >= kSize
    def hasSpace: Boolean = !isFull
  }

  override def ping(id: NodeId): Future[Unit] = ???

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
