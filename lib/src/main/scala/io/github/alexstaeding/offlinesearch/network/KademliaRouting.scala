package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import java.util
import java.util.UUID
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future

class KademliaRouting[V](
    private val idSpace: NodeIdSpace,
    private val localNodeId: NodeId,
    private val kMaxSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using
    private val env: {
      val network: Network
    },
) extends Routing[V] {

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

  private val openRequests: mutable.Map[UUID, OutgoingRequest] = new mutable.HashMap[UUID, OutgoingRequest]

  private def createRequest[N <: OutgoingRequest](factory: NetworkEvent.Factory[N], targetId: NodeId): N = {
    val id = UUID.randomUUID()
    val request = factory.create(id, targetId)
    openRequests.put(id, request)
    request
  }
  
  private def createRequestFuture[N <: OutgoingRequest](): Unit = {
    
  }

  private def distanceLeadingZeros(id: NodeId): Int = {
    val distance = localNodeId.xor(id)
    val firstByte = distance.bytes.indexWhere(_ != 0)
    val bitPrefix = Integer.numberOfLeadingZeros(distance.bytes(firstByte)) - 24
    firstByte * 8 + bitPrefix
  }

  private def getKBucket(distance: Int): KBucket = if (distance < buckets.size) buckets(distance) else homeBucket

  private def moveEntries[T](
      partitionIndex: Int,
      source: mutable.Map[NodeId, T],
      destination: mutable.Map[NodeId, T],
  ): Unit =
    source
      .collect { case (key, _) if distanceLeadingZeros(key) == partitionIndex => key }
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
    val index = distanceLeadingZeros(id)
    ensureBucketSpace(index) match {
      case Some(bucket) => {
        bucket.nodes.put(id, value)
        true
      }
      case None => false
    }
  }

  private def getClosest(targetId: NodeId): Seq[NodeInfo] = {
    val distance = distanceLeadingZeros(targetId)
    val bucket = getKBucket(distance)
    if (bucket.size >= concurrency) {
      bucket.nodes
        .to(LazyList)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .take(concurrency)
    } else {
      // TODO: Optimize by iteratively looking at buckets, starting with the closest
      (homeBucket +: buckets.to(LazyList))
        .flatMap(_.nodes.toSeq)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .take(concurrency)
    }
  }

  private def getLocalValue(targetId: NodeId): Option[V] = getKBucket(distanceLeadingZeros(targetId)).values.get(targetId)

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]()
    val values: mutable.Map[NodeId, V] = new mutable.HashMap[NodeId, V]()

    def size: Int = nodes.size + values.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalValue(targetId) match
      case Some(_) => return Future.successful(true)
      case None    =>

    val request = createRequest(PingEvent, targetId)

//    val event = PingEvent(targetId)
//    val closest = getClosest(targetId)
  }

  override def store(targetId: NodeId, value: V): Future[Boolean] = ???

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def findValue(targetId: NodeId): Future[V] = {
    getLocalValue(targetId) match
      case Some(value) => Future.successful(value)
      case None        => ???
  }
}
