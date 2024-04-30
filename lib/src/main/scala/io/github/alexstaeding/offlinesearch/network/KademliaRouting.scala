package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.event.*

import java.net.InetAddress
import java.util
import java.util.UUID
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.reflect.Selectable.reflectiveSelectable

class KademliaRouting[V](
    private val idSpace: NodeIdSpace,
    private val localNodeId: NodeId,
    private val kMaxSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using
    private val env: {
      val network: Network[V]
    },
) extends Routing[V] {

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

  private val openRequests: mutable.Map[UUID, RequestEvent] = new mutable.HashMap[UUID, RequestEvent]

  private def createRequest[N <: RequestEvent: RequestEvent.SimpleFactory](targetId: NodeId): N = {
    val id = UUID.randomUUID()
    val request = summon[RequestEvent.SimpleFactory[N]].create(id, targetId)
    openRequests.put(id, request)
    request
  }

  private def createRequestFuture[N <: RequestEvent](): Unit = {}

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

  private def getLocalNode(targetId: NodeId): Option[NodeInfo] = getKBucket(distanceLeadingZeros(targetId)).nodes.get(targetId)

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]()
    val values: mutable.Map[NodeId, V] = new mutable.HashMap[NodeId, V]()

    def size: Int = nodes.size + values.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull
  }

  @tailrec
  private def pingRemote(nextHop: InetAddress, targetId: NodeId): Future[Boolean] = {
    implicit val x: PingEvent.type = PingEvent
    val request = createRequest(targetId)
    Await.result(env.network.send(nextHop, request), 0.nanos) match
      case PingAnswerEvent(id) =>
        if (id != targetId) throw IllegalStateException(s"Received ping answer for incorrect id $id instead of $targetId")
        Future.successful(true)
      case NotFoundEvent(id) =>
        if (id != targetId) throw IllegalStateException(s"Received not found for incorrect id $id instead of $targetId")
        Future.successful(false)
      case RedirectEvent(id, closerTargetInfo) =>
        if (id != targetId) throw IllegalStateException(s"Received redirect for incorrect id $id instead of $targetId")
        // TODO: Save id?
        pingRemote(closerTargetInfo.ip, targetId)
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalValue(targetId) match
      case Some(_) => Future.successful(true)
      case _ =>
        getLocalNode(targetId) match
          case Some(node) => pingRemote(node.ip, targetId)
          case _ =>
            Future
              .find(getClosest(targetId).map { case NodeInfo(_, ip) => pingRemote(ip, targetId) })(identity)
              .map(_.getOrElse(false))
  }

  override def store(targetId: NodeId, value: V): Future[Boolean] = ???

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def findValue(targetId: NodeId): Future[V] = {
    getLocalValue(targetId) match
      case Some(value) => Future.successful(value)
      case None        => ???
  }
}
