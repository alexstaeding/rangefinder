package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.rangefinder.crdt.{GrowOnlyExpiryMap, SortedGrowOnlyExpiryMultiMap}
import io.github.alexstaeding.rangefinder.meta.{PartialKey, PartialKeyMatcher, PartialKeyUniverse}
import io.github.alexstaeding.rangefinder.network.IndexEntry.Funnel
import io.github.alexstaeding.rangefinder.network.NodeId.DistanceOrdering
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.time.OffsetDateTime
import java.util
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.collection.immutable.{ListMap, TreeMap}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

class KademliaRouting[V: JsonValueCodec, P: JsonValueCodec](
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
) extends Routing[V, P] {

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val network = networkFactory.create(InetSocketAddress(localNodeInfo.address.getPort), observerAddress, KademliaEventHandler)

  private val buckets: mutable.Buffer[KBucket] = new mutable.ArrayDeque[KBucket]

  /** The bucket for the zero distance
    */
  private val homeBucket: KBucket = new KBucket

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

  private def searchIndexGroup(indexGroup: IndexGroup, targetId: NodeId, searchKey: PartialKey[V]): Seq[IndexEntry[V, P]] = {
    val localEntries = indexGroup.search(searchKey)
    val funnelResults = funnelSearch(targetId, searchKey, localEntries.collect { case x: IndexEntry.Funnel[V] => x })
    val localResults = localEntries.collect { case x: IndexEntry.Value[V, P] => x }

    localResults ++ funnelResults
  }

  /** Performs a BFS on all provided funnels.
    */
  private def funnelSearch(targetId: NodeId, searchKey: PartialKey[V], funnels: Seq[IndexEntry.Funnel[V]]): Seq[IndexEntry[V, P]] = {

    case class PathEntry(funnel: IndexEntry.Funnel[V], prev: Option[PathEntry])

    val rootEntry = PathEntry(IndexEntry.Funnel(targetId, searchKey), None)
    val queue = mutable.ArrayDeque.from(funnels.map { f => PathEntry(f, Some(rootEntry)) })
    val results = new mutable.ArrayBuffer[IndexEntry[V, P]]

    while (queue.nonEmpty) {
      val current = queue.removeHead()
      // check for cycles
      if (
        Seq
          .unfold(current) { c => c.prev.map { p => (p, p) } }
          .forall { p => p.funnel.targetId == current.funnel.targetId }
      ) {
        getLocalValue(current.funnel.targetId) match
          // funnel to local index
          case Some(indexGroup) =>
            indexGroup.search(current.funnel.search).foreach {
              case f: IndexEntry.Funnel[V]   => queue.addOne(PathEntry(f, Some(current)))
              case v: IndexEntry.Value[V, P] => results.addOne(v)
            }
          // funnel to remote index
          // additional search is not performed at this time.
          // instead, aliases are sent in result set to the query initiator
          case None => results.addOne(current.funnel)
      }
    }

    results.toSeq
  }

  private object KademliaEventHandler extends EventHandler[V, P] {
    override def handlePing(request: PingEvent): Either[RedirectEvent, PingAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      if (request.targetId == localNodeInfo.id) {
        request.createAnswer(true)
      } else {
        getLocalValue(request.targetId) match
          case Some(_) => request.createAnswer(true)
          case None =>
            getLocalNode(request.targetId) match
              case Some(nodeInfo) => request.createRedirect(localNodeInfo, nodeInfo)
              case None =>
                getClosestBetterThan(request.targetId, localNodeInfo.id) match
                  case closest if closest.nonEmpty => request.createRedirect(localNodeInfo, closest.head)
                  case _                           => request.createAnswer(false)
      }
    }

    override def handleFindNode(request: FindNodeEvent): Either[RedirectEvent, FindNodeAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      if (request.targetId == localNodeInfo.id) {
        request.createAnswer(true)
      } else {
        getLocalNode(request.targetId) match
          case Some(_) => request.createAnswer(true)
          case None =>
            getClosestBetterThan(request.targetId, localNodeInfo.id) match
              case closest if closest.nonEmpty => request.createRedirect(localNodeInfo, closest.head)
              case _                           => request.createAnswer(false)
      }
    }

    override def handleSearch(request: SearchEvent[V, P]): Either[RedirectEvent, SearchAnswerEvent[V, P]] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      getLocalValue(request.targetId) match
        case Some(indexGroup) =>
          logger.info(s"Search: found local index group ${indexGroup.partialKey} for id ${request.targetId}")
          request.createAnswer(Some(searchIndexGroup(indexGroup, request.targetId, request.search)))
        case None =>
          logger.info(s"Search: did not find local index group for key ${request.targetId}, looking for closer nodes")
          getClosestBetterThan(request.targetId, localNodeInfo.id) match
            case closest if closest.nonEmpty => request.createRedirect(localNodeInfo, closest.head)
            case _                           => request.createAnswer(None)
    }

    override def handleStoreValue(request: StoreValueEvent[V, P]): Either[RedirectEvent, StoreValueAnswerEvent] = {
      putLocalNode(request.sourceInfo.id, request.sourceInfo)
      val localSuccess = putLocalValue(request.targetId, request.value)
      logger.info(s"Stored entry ${request.value} at id ${request.targetId} locally: $localSuccess")
      getClosestBetterThan(request.targetId, localNodeInfo.id) match
        case closest if closest.nonEmpty => request.createRedirect(localNodeInfo, closest.head)
        case _                           => request.createAnswer(localSuccess)
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
        moveEntries(buckets.size, homeBucket.values, newTail.values)
        buckets.addOne(newTail)
        ensureBucketSpace(index)
      }
    }
  }

  override def putLocal(id: NodeId, localValue: Either[NodeInfo, IndexEntry[V, P]]): Boolean = {
    val index = distanceLeadingZeros(id)
    ensureBucketSpace(index) match {
      case Some(bucket) =>
        localValue match
          case Right(entry) =>
            entry.getRootKeysOption.foreach { rootKeys =>
              rootKeys.foreach { rootKey =>
                bucket.ensureIndexGroup(id, rootKey) match
                  case Some(indexGroup: IndexGroup) => indexGroup.put(entry)
                  case None                         => logger.error(s"Full indexGroup $id")
              }
            }
          case Left(nodeInfo) => bucket.nodes.put(id, nodeInfo)
        sendObserverUpdate()
        true
      case None => false
    }
  }

  private def sendObserverUpdate(): Unit = {
    val nodes = (homeBucket +: buckets.to(LazyList)).flatMap(_.nodes.keys).map(x => PeerUpdate(x.toHex, "node"))
    val values = (homeBucket +: buckets.to(LazyList)).flatMap(_.values.keys).map(x => PeerUpdate(x.toHex, "entry"))
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

  private def getLocalValue(targetId: NodeId): Option[IndexGroup] = getKBucket(distanceLeadingZeros(targetId)).values.get(targetId)

  private def getLocalNode(targetId: NodeId): Option[NodeInfo] = getKBucket(distanceLeadingZeros(targetId)).nodes.get(targetId)

  private case class IndexGroup(partialKey: PartialKey[V]) {
    private var _values: SortedGrowOnlyExpiryMultiMap[V, IndexEntry.Value[V, P]] = new TreeMap
    private var _funnels: GrowOnlyExpiryMap[IndexEntry.Funnel[V]] = new ListMap

    def values: SortedGrowOnlyExpiryMultiMap[V, IndexEntry.Value[V, P]] = _values
    def funnels: GrowOnlyExpiryMap[IndexEntry.Funnel[V]] = _funnels

    private def searchValues(searchKey: PartialKey[V], now: OffsetDateTime): Seq[IndexEntry[V, P]] = {
      values
        .range(searchKey.startInclusive, searchKey.endExclusive)
        .to(LazyList)
        .flatMap { (_, m) => m }
        .filter { (_, expiry) => expiry.isAfter(now) }
        .map { (entry, _) => entry }
        .toList
    }

    private def searchFunnels(searchKey: PartialKey[V], now: OffsetDateTime): Seq[IndexEntry[V, P]] = {
      funnels
        .to(LazyList)
        .filter { (_, expiry) => expiry.isAfter(now) }
        .map { (funnel, _) => funnel }
        .filter { funnel => funnel.search.contains(searchKey) }
        .toList
    }

    def search(searchKey: PartialKey[V]): Seq[IndexEntry[V, P]] = {
      val now = OffsetDateTime.now()
      searchValues(searchKey, now) ++ searchFunnels(searchKey, now)
    }

    private def putValue(value: IndexEntry.Value[V, P]): Unit = {
      val one = SortedGrowOnlyExpiryMultiMap.ofOne(value.value, value)
      _values = SortedGrowOnlyExpiryMultiMap.lattice.merge(_values, one)
    }

    private def putFunnel(funnel: IndexEntry.Funnel[V]): Unit = {
      val one = GrowOnlyExpiryMap.ofOne(funnel)
      _funnels = GrowOnlyExpiryMap.lattice.merge(_funnels, one)
    }

    def put(entry: IndexEntry[V, P]): Unit =
      entry match
        case f: IndexEntry.Funnel[V]   => putFunnel(f)
        case v: IndexEntry.Value[V, P] => putValue(v)
  }

  private class KBucket {
    val nodes: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap[NodeId, NodeInfo]
    val values: mutable.Map[NodeId, IndexGroup] = new mutable.HashMap[NodeId, IndexGroup]

    def size: Int = nodes.size + values.size
    def isFull: Boolean = size >= kMaxSize
    def hasSpace: Boolean = !isFull

    def ensureIndexGroup(id: NodeId, key: PartialKey[V]): Option[IndexGroup] = {
      if (isFull) None else Some(values.getOrElseUpdate(id, IndexGroup(key)))
    }
  }

  private def remoteCall[C, A <: AnswerEvent[V, P] { type Content <: C }, R <: RequestEvent[V, P] { type Answer <: A }](
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
        case Left(RedirectEvent(requestId, _, closerTargetInfo)) =>
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
          case Some(node) => remoteCall(node.address, RequestEvent.createPing(localNodeInfo, targetId, node))
          case _ =>
            Future
              .find(getClosest(targetId).map { case nextHopPeer @ NodeInfo(_, address) =>
                remoteCall(address, RequestEvent.createPing(localNodeInfo, targetId, nextHopPeer)).recover { exception =>
                  logger.error(s"Failed to send remote ping to $nextHopPeer", exception)
                  false
                }
              })(identity)
              .map(_.getOrElse(false))
  }

  private def storeOne(rootKey: PartialKey[V], entry: IndexEntry[V, P]): Future[Boolean] = {
    val targetId = hashingAlgorithm.hash(rootKey)
    logger.info(s"Determined hash for rootKey $rootKey -> ${targetId.toHex}")
    Future
      .find(getClosest(targetId).map { case nextHopPeer @ NodeInfo(_, address) =>
        remoteCall(address, RequestEvent.createStoreValue(localNodeInfo, targetId, nextHopPeer, entry)).recover { exception =>
          logger.error(s"Failed to send remote store to $nextHopPeer", exception)
          false
        }
      })(identity)
      .map(_.getOrElse(false))
  }

  override def store(entry: IndexEntry[V, P]): Future[Boolean] = {
    val rootKeys = entry.getRootKeysOption match
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

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def search(key: PartialKey[V]): Future[Seq[IndexEntry[V, P]]] = {
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
