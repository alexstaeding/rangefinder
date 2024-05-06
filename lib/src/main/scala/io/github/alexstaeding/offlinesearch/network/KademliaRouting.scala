package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.offlinesearch.network.NodeId.DistanceOrdering
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util
import java.util.UUID
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Failure, Success}

class KademliaRouting[V: JsonValueCodec](
    private val networkFactory: NetworkAdapter.Factory,
    private val localNodeInfo: NodeInfo,
    private val kMaxSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using idSpace: NodeIdSpace, logger: Logger)
    extends Routing[V] {

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val network = networkFactory.create(localNodeInfo.address, receive)

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

  private def getKBucket(leadingZeros: Int): KBucket = if (leadingZeros < buckets.size) buckets(leadingZeros) else homeBucket

  private def moveEntries[T](
      partitionIndex: Int,
      source: mutable.Map[NodeId, T],
      destination: mutable.Map[NodeId, T],
  ): Unit =
    source
      .collect { case (key, _) if distanceLeadingZeros(key) == partitionIndex => key }
      .foreach { key => destination.put(key, source.remove(key).get) }

  private def receive(request: RequestEvent[V]): AnswerEvent[V] = {
    logger.info(s"Received: $request")
    putLocal(request.sourceInfo.id, request.sourceInfo)
    val answer: AnswerEvent[V] = request match
      case PingEvent(requestId, sourceInfo, targetId) =>
        if (targetId == localNodeInfo.id) {
          PingAnswerEvent(requestId, success = true)
        } else {
          getLocalValue(targetId) match
            case Some(value) => PingAnswerEvent(requestId, success = true)
            case None =>
              getLocalNode(targetId) match
                case Some(nodeInfo) => RedirectEvent(requestId, nodeInfo)
                case None =>
                  getClosestBetterThan(targetId, localNodeInfo.id) match
                    case closest if closest.nonEmpty => RedirectEvent(requestId, closest.head)
                    case _                           => PingAnswerEvent(requestId, success = false)
        }
      case request @ _ => throw IllegalStateException(s"Unexpected request received: $request")
    logger.info(s"Answering: $answer")
    answer
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

  private def pingRemote(nextHop: InetSocketAddress, targetId: NodeId): Future[Boolean] = {
    logger.info(s"Pinging remote $nextHop with target $targetId")
    implicit val x: PingEvent.type = PingEvent
    val sentRequest = createRequest { requestId => PingEvent(requestId, localNodeInfo, targetId) }
    network
      .send(nextHop, sentRequest)
      .flatMap[Boolean] {
        case PingAnswerEvent(requestId, success) =>
          if (requestId != sentRequest.requestId)
            throw IllegalStateException(s"Received ping answer for incorrect id $requestId instead of $targetId")
          logger.info(s"Ping($requestId) returned $success")
          Future.successful(success)
        case RedirectEvent(requestId, closerTargetInfo) =>
          if (requestId != sentRequest.requestId)
            throw IllegalStateException(s"Received redirect for incorrect id $requestId instead of $targetId")
          logger.info(s"Redirecting Ping($requestId) to closer target: $closerTargetInfo")
          pingRemote(closerTargetInfo.address, targetId)
        case answer => throw IllegalStateException(s"Unexpected answer received: $answer")
      }
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalValue(targetId) match
      case Some(_) => Future.successful(true)
      case _ =>
        getLocalNode(targetId) match
          case Some(node) => pingRemote(node.address, targetId)
          case _ =>
            Future
              .find(
                getClosest(targetId)
                  .map {
                    case nodeInfo@NodeInfo(_, ip) =>
                      pingRemote(ip, targetId).andThen {
                        case Failure(exception) =>
                          logger.error(s"Failed to send remote ping $nodeInfo", exception)
                          false
                      }
                  },
              )(identity)
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
