package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.NodeId.DistanceOrdering

import java.net.{InetAddress, InetSocketAddress}
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
    private val localNodeInfo: NodeInfo,
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

  private val openRequests: mutable.Map[UUID, RequestEvent[V]] = new mutable.HashMap[UUID, RequestEvent[V]]

  private def distanceLeadingZeros(id: NodeId): Int = {
    val distance = localNodeInfo.id.xor(id)
    val firstByte = distance.bytes.indexWhere(_ != 0)
    if (firstByte == -1) {
      // no difference, self
      return 0
    }
    val bitPrefix = Integer.numberOfLeadingZeros(distance.bytes(firstByte) & 0xff) - 24
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

  def startReceiver(): Unit = {
    println("Starting receiver...")
    ec.submit(new Runnable {
      override def run(): Unit = {
        println("Inside receiver")
        while (true) {
          println("Waiting...")
          receive()
        }
      }
    })
  }

  private def receive(): Unit = {
    val interceptor = Await.result(env.network.receive(), 2.seconds)
    val request = interceptor.request
    putLocal(request.sourceInfo.id, request.sourceInfo)
    val answer: AnswerEvent[V] = request match
      case PingEvent(requestId, sourceInfo, targetId) =>
        getLocalValue(targetId) match
          case Some(value) => PingAnswerEvent(requestId, success = true)
          case None =>
            getLocalNode(targetId) match
              case Some(nodeInfo) => RedirectEvent(requestId, nodeInfo)
              case None =>
                getClosestBetterThan(targetId, localNodeInfo.id) match
                  case closest if closest.nonEmpty => RedirectEvent(requestId, closest.head)
                  case _ => PingAnswerEvent(requestId, success = false)
      case request @ _ => throw IllegalStateException(s"Unexpected request received: $request")

    interceptor.answer(answer)
  }

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

  override def putLocal(id: NodeId, value: NodeInfo | V): Boolean = {
    val index = distanceLeadingZeros(id)
    ensureBucketSpace(index) match {
      case Some(bucket) => {
        value match
          case nodeInfo @ NodeInfo(_, _) => bucket.nodes.put(id, nodeInfo)
          case _                         => bucket.values.put(id, value.asInstanceOf[V])
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

  private def getClosestBetterThan(targetId: NodeId, than: NodeId): Seq[NodeInfo] =
    getClosest(targetId).filter(info => DistanceOrdering(targetId).compare(info.id, than) < 0)

  private def getLocalValue(targetId: NodeId): Option[V] = getKBucket(distanceLeadingZeros(targetId)).values.get(targetId)

  private def getLocalNode(targetId: NodeId): Option[NodeInfo] = getKBucket(distanceLeadingZeros(targetId)).nodes.get(targetId)

  private def getLocal(targetId: NodeId): Option[NodeInfo | V] = getLocalNode(targetId).orElse(getLocalValue(targetId))

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]()
    val values: mutable.Map[NodeId, V] = new mutable.HashMap[NodeId, V]()

    def size: Int = nodes.size + values.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull
  }

  private def createRequest[N <: RequestEvent[V]](ctor: UUID => N): N = {
    val id = UUID.randomUUID()
    val request = ctor(id)
    openRequests.put(id, request)
    request
  }

  @tailrec
  private def pingRemote(nextHop: InetSocketAddress, targetId: NodeId): Future[Boolean] = {
    println(s"Pinging remote $nextHop with target $targetId")
    implicit val x: PingEvent.type = PingEvent
    val request = createRequest { requestId => PingEvent(requestId, localNodeInfo, targetId) }
    Await.result(env.network.send(nextHop, request), 2.seconds) match
      case PingAnswerEvent(id, success) =>
        if (id != targetId) throw IllegalStateException(s"Received ping answer for incorrect id $id instead of $targetId")
        Future.successful(success)
      case RedirectEvent(id, closerTargetInfo) =>
        if (id != targetId) throw IllegalStateException(s"Received redirect for incorrect id $id instead of $targetId")
        // TODO: Save id?
        pingRemote(closerTargetInfo.address, targetId)
      case answer @ _ => throw IllegalStateException(s"Unexpected answer received: $answer")
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalValue(targetId) match
      case Some(_) => Future.successful(true)
      case _ =>
        getLocalNode(targetId) match
          case Some(node) => pingRemote(node.address, targetId)
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
