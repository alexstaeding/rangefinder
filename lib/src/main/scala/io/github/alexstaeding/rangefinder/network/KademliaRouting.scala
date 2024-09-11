package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.rangefinder.crdt.{GrowOnlyExpiryMap, SortedGrowOnlyExpiryMultiMap}
import io.github.alexstaeding.rangefinder.future.withTimeout
import io.github.alexstaeding.rangefinder.meta.{LocalIndex, PartialKey, PartialKeyMatcher, PartialKeyUniverse}
import io.github.alexstaeding.rangefinder.network.IndexEntry.Funnel
import io.github.alexstaeding.rangefinder.network.NodeId.DistanceOrdering
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.time.OffsetDateTime
import java.util
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, LinkedBlockingDeque, PriorityBlockingQueue}
import scala.annotation.tailrec
import scala.collection.immutable.{ListMap, TreeMap}
import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Failure, Success}

class KademliaRouting[V: JsonValueCodec: Ordering: PartialKeyMatcher, P: JsonValueCodec](
    private val networkFactory: NetworkAdapter.Factory,
    private val localNodeInfo: NodeInfo,
    private val observerAddress: Option[InetSocketAddress],
    private val contentUrl: Option[String] = None,
    private val localContentKeys: Option[Seq[String]] = None,
    private val kMaxSize: Int = 20, // Size of K-Buckets
    private val concurrency: Int = 3, // Number of concurrent searches
)(using
    logger: Logger,
    universe: PartialKeyUniverse[V],
    hashingAlgorithm: HashingAlgorithm[V],
) extends Routing[V, P] {

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val network = networkFactory.create(InetSocketAddress(localNodeInfo.address.getPort), observerAddress, KademliaEventHandler)

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

  private val localIndex = new LocalIndex

  private def verifyTargetPeer(targetPeer: NodeInfo): Unit = {
    if (targetPeer.id != localNodeInfo.id) {
      throw IllegalArgumentException(s"Target peer ID ${targetPeer.id} does not match local ID ${localNodeInfo.id}")
    }
  }

  logger.info("Sending initial observer update")
  network.sendObserverUpdate(NodeInfoUpdate(localNodeInfo.id.toHex, Seq.empty, contentUrl, localContentKeys))

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

  private object KademliaEventHandler extends EventHandler[V, P] {
    override def handlePing(request: PingEvent): Either[ErrorEvent, PingAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      request.createAnswer(request.targetId == localNodeInfo.id)
    }

    override def handleFindNode(request: FindNodeEvent): Either[ErrorEvent, FindNodeAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      if (request.targetId == localNodeInfo.id) {
        request.createAnswer(Seq.empty)
      } else {
        getLocalNode(request.targetId) match
          case Some(localNode) => request.createAnswer(Seq(localNode))
          case None            => request.createAnswer(getClosestBetterThan(request.targetId, localNodeInfo.id))
      }
    }

    override def handleSearch(request: SearchEvent[V, P]): Either[ErrorEvent, SearchAnswerEvent[V, P]] = {
      val now = OffsetDateTime.now
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      request.createAnswer(localIndex.search(request.targetId, request.searchKey, now))
    }

    override def handleStoreValue(request: StoreValueEvent[V, P]): Either[ErrorEvent, StoreValueAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      val localSuccess = putLocalValue(request.targetId, request.value)
      logger.info(s"Stored entry ${request.value} at id ${request.targetId} locally: $localSuccess")
      request.createAnswer(localSuccess)
    }
  }

  @tailrec
  private def ensureBucketSpace(index: Int): Option[KBucket] = {
    if (buckets.size > index) {
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
        buckets.addOne(newTail)
        ensureBucketSpace(index)
      }
    }
  }

  override def putLocalNode(id: NodeId, nodeInfo: NodeInfo): Boolean = {
    val indexNr = distanceLeadingZeros(id)
    ensureBucketSpace(indexNr) match {
      case Some(bucket) =>
        bucket.nodes.put(id, nodeInfo)
        sendObserverUpdate()
        true
      case None => false
    }
  }

  override def putLocalValue(id: NodeId, entry: IndexEntry[V, P]): Boolean = ???

  private def sendObserverUpdate(): Unit = {
    val nodes = (homeBucket +: buckets.to(LazyList)).flatMap(_.nodes.keys).map(x => PeerUpdate(x.toHex, "node"))
    val values = localIndex.getIds.map(x => PeerUpdate(x.toHex, "entry"))
    network.sendObserverUpdate(NodeInfoUpdate(localNodeInfo.id.toHex, nodes ++ values, contentUrl, localContentKeys))
  }

  private def getClosest(targetId: NodeId): Seq[NodeInfo] = {
    val distance = distanceLeadingZeros(targetId)
    val bucket = getKBucket(distance)
    val result = if (bucket.size >= concurrency) {
      bucket.nodes
        .to(LazyList)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .filterNot { info => info.id == localNodeInfo.id || info.address == localNodeInfo.address }
        .take(concurrency)
    } else {
      // TODO: Optimize by iteratively looking at buckets, starting with the closest
      (homeBucket +: buckets.to(LazyList))
        .flatMap(_.nodes.toSeq)
        .sortBy(_._1)(using NodeId.DistanceOrdering(targetId))
        .map(_._2)
        .filterNot { info => info.id == localNodeInfo.id || info.address == localNodeInfo.address }
        .take(concurrency)
    }
    logger.info("Closest nodes: " + result.map(_.id.toHex).mkString(", "))
    result
  }

  private def getClosestBetterThan(targetId: NodeId, than: NodeId): Seq[NodeInfo] =
    getClosest(targetId).filter(info => DistanceOrdering(targetId).compare(info.id, than) < 0)

  private def getLocalNode(targetId: NodeId): Option[NodeInfo] = getKBucket(distanceLeadingZeros(targetId)).nodes.get(targetId)

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]
    def size: Int = nodes.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull
  }

  override def ping(targetId: NodeId): Future[Boolean] = {
    getLocalNode(targetId) match
      case Some(node) =>
        network
          .send(node.address, RequestEvent.createPing(localNodeInfo, targetId, node))
          .map(_.content)
          .withTimeout(5.seconds, false)
      case None => Future.successful(false)
  }

  private def storeOne(rootKey: PartialKey[V], entry: IndexEntry[V, P]): Future[Boolean] = {
    val targetId = hashingAlgorithm.hash(rootKey)
    logger.info(s"Determined hash for rootKey $rootKey -> ${targetId.toHex}")
    (
      for {
        target <- findNode(targetId)
        result <- network.send(target.address, RequestEvent.createStoreValue(localNodeInfo, targetId, target, entry))
      } yield result.content
    ) recover { case e: Exception =>
      logger.error("Failed to execute storeOne", e)
      false
    }
  }

  override def store(entry: IndexEntry[V, P]): Future[Boolean] = {
    val rootKeys = entry.getIndexKeysOption match
      case Some(value) => value
      case None        => return Future.successful(false)
    Future
      .sequence(
        rootKeys.map { k => storeOne(k, entry) }.map { f =>
          f.recover { case e: Exception =>
            logger.error(s"Failed to store entry $entry", e)
            false
          }
        },
      )
      .map(_.exists(identity)) // sent to at least one peer
  }

  private def remoteCall[C, A <: AnswerEvent[V, P] { type Content <: C }, R <: RequestEvent[V, P] { type Answer <: A }](
      nextHopAddress: InetSocketAddress,
      originator: R,
  ): Future[C] = {
    logger.info(s"Sending RPC to $nextHopAddress with target ${originator.targetId}")
    network
      .send(nextHopAddress, originator)
      .recoverWith { e =>
        logger.error(s"Failed to send remote call to $nextHopAddress", e)
        Future.failed(e)
      }
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
        case Left(RedirectEvent(requestId, _, closerTargetInfo)) =>
          logger.info(s"Redirecting Request($requestId) to closer target: $closerTargetInfo")
          remoteCall(closerTargetInfo.address, originator)
        case Right(value) => Future.successful(value.content)
      }
  }

  override def findNode(targetId: NodeId): Future[NodeInfo] = {

    Future {
      val workingQueue = new PriorityBlockingQueue[NodeInfo](concurrency * concurrency, DistanceOrdering(targetId).asNodeInfo)
      val results = new PriorityBlockingQueue[NodeInfo](concurrency * concurrency, DistanceOrdering(targetId).asNodeInfo)
      val futures = new LinkedBlockingDeque[Future[FindNodeAnswerEvent]]

      def sendFutures(): Unit = {
        getClosest(targetId).foreach { nodeId =>
          network
            .send(nodeId.address, RequestEvent.createFindNode(localNodeInfo, targetId, nodeId))
            .map { (answer: FindNodeAnswerEvent) => answer.content.foreach(workingQueue.add) }
            .recover { case e: Exception =>
              logger.error(s"Failed to send findNode to $nodeId", e)
              false
            }
        }
      }

      while (!futures.isEmpty || !results.isEmpty) {
        val it = futures.iterator()
        while (it.hasNext) {
          val nextFuture = it.next
          if (nextFuture.isCompleted) {

            it.remove()
          }
        }
      }

//    Future
//      .find(getClosest(targetId).map { case nextHopPeer@NodeInfo(_, address) =>
//        remoteCall(address, RequestEvent.createStoreValue(localNodeInfo, targetId, nextHopPeer, entry)).recover { exception =>
//          logger.error(s"Failed to send remote store to $nextHopPeer", exception)
//          false
//        }
//      })(identity)
//      .map(_.getOrElse(false))
      ???
    }
  }

  override def search(key: PartialKey[V]): Future[Set[IndexEntry[V, P]]] = {
    val rootKeys =
      try universe.getIndexKeys(key)
      catch
        case e: Exception =>
          logger.error(s"Failed to get root keys for key $key", e)
          return Future.successful(Set.empty)
    logger.info(s"Search query $key matches root keys $rootKeys")
    // wait for all futures to complete and return full result
    Future.sequence(rootKeys.map(x => search(x, key))).map(_.flatten)
  }

  private def search(rootKey: PartialKey[V], searchKey: PartialKey[V]): Future[Seq[IndexEntry[V, P]]] = {
    val targetId = hashingAlgorithm.hash(rootKey)
    getLocalValue(targetId) match
      case Some(indexGroup) =>
        logger.info(s"Found local index group ${indexGroup.partialKey}")
        Future.successful(searchIndexGroup(indexGroup, targetId, searchKey))
      case None =>
        logger.info(s"Looking for remote index group")
        Future
          .find(getClosest(targetId).map { case nextHopPeer @ NodeInfo(_, address) =>
            remoteCall(address, RequestEvent.createSearch(localNodeInfo, targetId, nextHopPeer, searchKey)).recover { exception =>
              logger.error(s"Failed to send remote search $nextHopPeer", exception)
              None
            }
          })(_.isDefined)
          .map(_.flatten.getOrElse(Seq.empty))
  }
}
