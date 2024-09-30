package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.rangefinder.meta.PartialKey
import io.github.alexstaeding.rangefinder.network.NodeId.DistanceOrdering
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.{Failure, Success}
class BroadcastRouting[V: JsonValueCodec: Ordering, P: JsonValueCodec](
    private val networkFactory: NetworkAdapter.Factory,
    private val localNodeInfo: NodeInfo,
    private val observerAddress: Option[InetSocketAddress],
    private val contentUrl: Option[String] = None,
    private val localContentKeys: Option[Seq[String]] = None,
)(using logger: Logger)
    extends Routing[V, P] {

  private val peers: mutable.Map[NodeId, NodeInfo] = new mutable.HashMap
  private val values: mutable.SortedMap[V, IndexEntry.Value[V, P]] = new mutable.TreeMap
  private val funnels: mutable.Set[IndexEntry.Funnel[V]] = new mutable.LinkedHashSet

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))

  private val network = networkFactory.create(InetSocketAddress(localNodeInfo.address.getPort), observerAddress, BroadcastEventHandler)

  override def ping(targetId: NodeId): Future[Boolean] = ???

  override def store(entry: IndexEntry[V, P]): Future[Boolean] = ???

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def search(key: PartialKey[V]): Future[Set[IndexEntry[V, P]]] = ???

  override def searchWithPath(key: PartialKey[V]): Future[Set[(IndexEntry[V, P], NodePath)]] = ???

  override def putLocalNode(node: NodeInfo): Boolean = ???

  override def putLocalValue(id: NodeId, entry: IndexEntry[V, P]): Boolean = ???

  private object BroadcastEventHandler extends EventHandler[V, P] {

    private def broadcast(localNodeInfo: NodeInfo, lastHopPeer: NodeInfo, event: RequestEvent[V, P]): Unit = {
      Await.result(
        Future.sequence(
          peers.values
            .to(LazyList)
            .filterNot { peer => peer.id == lastHopPeer.id }
            .flatMap { peer =>
              event.forward(localNodeInfo, lastHopPeer).map { forwardEvent =>
                network.send(peer.address, forwardEvent).transform {
                  case Failure(exception) =>
                    Success(logger.warn("Failed to forward ping {} to {} with exception {}", forwardEvent, peer, exception))
                  case Success(_) =>
                    Success(logger.info("Successfully forwarded ping {} to {}", forwardEvent, peer))
                }
              }
            },
        ),
        5.seconds,
      )
    }

    override def handlePing(request: PingEvent): Either[ErrorEvent, PingAnswerEvent] = request match
      case PingEvent(_, _, targetId, _) if targetId == localNodeInfo.id => request.createAnswer(true)
      case event @ PingEvent(_, _, _, RoutingInfo(lastHopPeer, _, _)) =>
        broadcast(localNodeInfo, lastHopPeer, event)
        request.createAnswer(false)

    override def handleFindNode(request: FindNodeEvent): Either[ErrorEvent, FindNodeAnswerEvent] = {
      implicit val ordering: Ordering[NodeInfo] = DistanceOrdering(request.targetId).asNodeInfo
      request match
        case FindNodeEvent(_, _, targetId, _) if targetId == localNodeInfo.id => request.createAnswer(Seq.empty)
        case event @ FindNodeEvent(_, _, _, RoutingInfo(lastHopPeer, _, _)) =>
          broadcast(localNodeInfo, lastHopPeer, event)
          request.createAnswer(peers.values.filter { node => node < localNodeInfo && node < request.sourceInfo }.toSeq)
    }

    override def handleSearch(request: SearchEvent[V, P]): Either[ErrorEvent, SearchAnswerEvent[V, P]] = {
      implicit val ordering: Ordering[NodeInfo] = DistanceOrdering(request.targetId).asNodeInfo
      request.createAnswer(
        SearchAnswerContent(
          peers.values.filter { node => node < localNodeInfo && node < request.sourceInfo }.toSeq,
          values
            .range(request.searchKey.startInclusive, request.searchKey.endExclusive)
            .values
            .toSeq,
        ),
      )
    }

    override def handleStoreValue(request: StoreValueEvent[V, P]): Either[ErrorEvent, StoreValueAnswerEvent] = request match
      case StoreValueEvent(_, _, targetId, _, value, _) if targetId == localNodeInfo.id =>
        value match
          case f: IndexEntry.Funnel[V]   => funnels.addOne(f)
          case v: IndexEntry.Value[V, P] => values.put(v.value, v)
        request.createAnswer(true)
      case event @ StoreValueEvent(_, _, _, RoutingInfo(lastHopPeer, _, _), _, _) =>
        broadcast(localNodeInfo, lastHopPeer, event)
        request.createAnswer(false)
  }
}
