package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.offlinesearch.meta.{PartialKey, PartialKeyMatcher, PartialKeyUniverse}
import io.github.alexstaeding.offlinesearch.network.NodeId.DistanceOrdering
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

class KademliaRouting[V: JsonValueCodec](
    private val networkFactory: NetworkAdapter.Factory,
    private val localNodeInfo: NodeInfo,
    private val observerAddress: Option[InetSocketAddress],
    private val contentUrl: Option[String] = None,
    private val localContentKeys: Option[Seq[String]] = None,
    private val kMaxSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using
    idSpace: NodeIdSpace,
    logger: Logger,
    universe: PartialKeyUniverse[V],
    partialKeyMatcher: PartialKeyMatcher[V],
    ordering: Ordering[V],
    hashingAlgorithm: HashingAlgorithm[V],
) extends Routing[V] {

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val network = networkFactory.create(localNodeInfo.address, observerAddress, KademliaEventReceiver)

  logger.info("Sending initial observer update")
  network.sendObserverUpdate(NodeInfoUpdate(localNodeInfo.id.toHex, Seq.empty, contentUrl, localContentKeys))

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

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

  private object KademliaEventReceiver extends EventReceiver[V] {
    override def receivePing(request: PingEvent[V]): Either[RedirectEvent[V], PingAnswerEvent[V]] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      if (request.targetId == localNodeInfo.id) {
        request.createAnswer(true)
      } else {
        getLocalValue(request.targetId) match
          case Some(value) => request.createAnswer(true)
          case None =>
            getLocalNode(request.targetId) match
              case Some(nodeInfo) => request.createRedirect(nodeInfo)
              case None =>
                getClosestBetterThan(request.targetId, localNodeInfo.id) match
                  case closest if closest.nonEmpty => request.createRedirect(closest.head)
                  case _                           => request.createAnswer(false)
      }
    }

    override def receiveFindNode(request: FindNodeEvent[V]): Either[RedirectEvent[V], FindNodeAnswerEvent[V]] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      if (request.targetId == localNodeInfo.id) {
        request.createAnswer(true)
      } else {
        getLocalNode(request.targetId) match
          case Some(_) => request.createAnswer(true)
          case None =>
            getClosestBetterThan(request.targetId, localNodeInfo.id) match
              case closest if closest.nonEmpty => request.createRedirect(closest.head)
              case _                           => request.createAnswer(false)
      }
    }

    override def receiveSearch(request: SearchEvent[V]): Either[RedirectEvent[V], SearchAnswerEvent[V]] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      getLocalValue(request.targetId) match
        case Some(indexGroup) =>
          logger.info(s"Search: found local index group ${indexGroup.partialKey} for id ${request.targetId}")
          request.createAnswer(Some(indexGroup.values.to(LazyList).filter { x => request.search.matches(x.value) }.toList))
        case None =>
          logger.info(s"Search: did not find local index group for key ${request.targetId}, looking for closer nodes")
          getClosestBetterThan(request.targetId, localNodeInfo.id) match
            case closest if closest.nonEmpty => request.createRedirect(closest.head)
            case _                           => request.createAnswer(None)
    }

    override def receiveStoreValue(request: StoreValueEvent[V]): Either[RedirectEvent[V], StoreValueAnswerEvent[V]] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      val localSuccess = putLocalValue(request.targetId, request.value)
      logger.info(s"Stored value ${request.value.value} at id ${request.targetId} locally: $localSuccess")
      getClosestBetterThan(request.targetId, localNodeInfo.id) match
        case closest if closest.nonEmpty => request.createRedirect(closest.head)
        case _                           => request.createAnswer(localSuccess)
    }
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

  override def putLocal(id: NodeId, localValue: Either[NodeInfo, OwnedValue[V]]): Boolean = {
    val index = distanceLeadingZeros(id)
    ensureBucketSpace(index) match {
      case Some(bucket) =>
        localValue match
          case Right(ownedValue) =>
            bucket.ensureIndexGroup(id, universe.getRootKey(ownedValue.value)) match
              case Some(indexGroup: IndexGroup) => indexGroup.values.addOne(ownedValue)
              case None                         => logger.error(s"Full indexGroup $id")
          case Left(nodeInfo) => bucket.nodes.put(id, nodeInfo)
        sendObserverUpdate()
        true
      case None => false
    }
  }

  private def sendObserverUpdate(): Unit = {
    val nodes = (homeBucket +: buckets.to(LazyList)).flatMap(_.nodes.keys).map(x => PeerUpdate(x.toHex, "node"))
    val values = (homeBucket +: buckets.to(LazyList)).flatMap(_.values.keys).map(x => PeerUpdate(x.toHex, "value"))
    network.sendObserverUpdate(NodeInfoUpdate(localNodeInfo.id.toHex, nodes ++ values, contentUrl, localContentKeys))
  }

  private def getClosest(targetId: NodeId): Seq[NodeInfo] = {
    val distance = distanceLeadingZeros(targetId)
    val bucket = getKBucket(distance)
    if (bucket.size >= concurrency) {
      bucket.nodes
        .to(LazyList)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .filterNot(_.id == localNodeInfo.id)
        .take(concurrency)
    } else {
      // TODO: Optimize by iteratively looking at buckets, starting with the closest
      (homeBucket +: buckets.to(LazyList))
        .flatMap(_.nodes.toSeq)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .filterNot(_.id == localNodeInfo.id)
        .take(concurrency)
    }
  }

  private def getClosestBetterThan(targetId: NodeId, than: NodeId): Seq[NodeInfo] =
    getClosest(targetId).filter(info => DistanceOrdering(targetId).compare(info.id, than) < 0)

  private def getLocalValue(targetId: NodeId): Option[IndexGroup] = getKBucket(distanceLeadingZeros(targetId)).values.get(targetId)

  private def getLocalNode(targetId: NodeId): Option[NodeInfo] = getKBucket(distanceLeadingZeros(targetId)).nodes.get(targetId)

  private def getLocal(targetId: NodeId): Option[NodeInfo | IndexGroup] = getLocalNode(targetId).orElse(getLocalValue(targetId))

  private case class IndexGroup(
      partialKey: PartialKey[V],
  ) {
    val values: mutable.TreeSet[OwnedValue[V]] = new mutable.TreeSet[OwnedValue[V]]()
  }

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]()
    val values: mutable.Map[NodeId, IndexGroup] = new mutable.HashMap[NodeId, IndexGroup]()

    def size: Int = nodes.size + values.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull

    def ensureIndexGroup(id: NodeId, key: PartialKey[V]): Option[IndexGroup] = {
      if (isFull) None else Some(values.getOrElseUpdate(id, IndexGroup(key)))
    }
  }

  private def remoteCall[C, A <: AnswerEvent[V] { type Content <: C }, R <: RequestEvent[V] { type Answer <: A }](
      nextHopAddress: InetSocketAddress,
      originator: R,
  ): Future[C] = {
    logger.info(s"Sending RPC to $nextHopAddress with target ${originator.targetId}")
    network
      .send(nextHopAddress, originator)
      .map { answer =>
        logger.info(s"Received answer $answer for ${originator.targetId}")
        val answerId = answer match
          case Left(value)  => value.requestId
          case Right(value) => value.requestId
        if (answerId != originator.requestId)
          throw IllegalStateException(s"Received answer for incorrect id $answerId instead of ${originator.targetId}")
        answer
      }
      .flatMap {
        case Left(RedirectEvent(requestId, closerTargetInfo)) =>
          logger.info(s"Redirecting Request($requestId) to closer target: $closerTargetInfo")
          remoteCall(closerTargetInfo.address, originator)
        case Right(value) => Future.successful(value.content)
      }
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalValue(targetId) match
      case Some(_) => Future.successful(true)
      case _ =>
        getLocalNode(targetId) match
          case Some(node) => remoteCall(node.address, RequestEvent.createPing(localNodeInfo, targetId))
          case _ =>
            Future
              .find(getClosest(targetId).map { case nodeInfo @ NodeInfo(_, address) =>
                remoteCall(address, RequestEvent.createPing(localNodeInfo, targetId)).recover { exception =>
                  logger.error(s"Failed to send remote ping to $nodeInfo", exception)
                  false
                }
              })(identity)
              .map(_.getOrElse(false))
  }

  override def store(ownedValue: OwnedValue[V]): Future[Boolean] = {
    val rootKey =
      try universe.getRootKey(ownedValue.value)
      catch
        case e: Exception =>
          logger.error(s"Failed to get root key for value ${ownedValue.value}", e)
          return Future.successful(false)
    val targetId = hashingAlgorithm.hash(rootKey)
    logger.info(s"Determined hash for rootKey $rootKey -> ${targetId.toHex}")
    Future
      .find(getClosest(targetId).map { case nodeInfo @ NodeInfo(_, address) =>
        remoteCall(address, RequestEvent.createStoreValue(localNodeInfo, targetId, ownedValue)).recover { exception =>
          logger.error(s"Failed to send remote store to $nodeInfo", exception)
          false
        }
      })(identity)
      .map(_.getOrElse(false))
  }

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def search(key: PartialKey[V]): Future[Seq[OwnedValue[V]]] = {
    val rootKeys =
      try universe.getOverlappingRootKeys(key)
      catch
        case e: Exception =>
          logger.error(s"Failed to get root keys for key $key", e)
          return Future.successful(Seq.empty)
    logger.info(s"Search query $key matches root keys $rootKeys")
    // wait for all futures to complete and return full result
    Future.sequence(rootKeys.map(x => search(x, key))).map(_.flatten)
  }

  private def search(rootKey: PartialKey[V], searchKey: PartialKey[V]): Future[Seq[OwnedValue[V]]] = {
    val targetId = hashingAlgorithm.hash(rootKey)
    getLocalValue(targetId) match
      case Some(indexGroup) =>
        logger.info(s"Found local index group ${indexGroup.partialKey}")
        Future.successful(indexGroup.values.to(LazyList).filter { x => searchKey.matches(x.value) }.toList)
      case None =>
        logger.info(s"Looking for remote index group")
        Future
          .find(getClosest(targetId).map { case nodeInfo @ NodeInfo(_, address) =>
            remoteCall(address, RequestEvent.createSearch(localNodeInfo, targetId, searchKey)).recover { exception =>
              logger.error(s"Failed to send remote search $nodeInfo", exception)
              None
            }
          })(_.isDefined)
          .map(_.flatten.getOrElse(Seq.empty))
  }
}
