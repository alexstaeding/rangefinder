package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.rangefinder.meta.PartialKey
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
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
  private val values: mutable.SortedMap[V, IndexEntry.Value[V]] = new mutable.TreeMap

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val network = networkFactory.create(InetSocketAddress(localNodeInfo.address.getPort), observerAddress, BroadcastEventHandler)

  override def ping(targetId: NodeId): Future[Boolean] = ???

  override def store(value: IndexEntry.Value[V]): Future[Boolean] = ???

  override def findNode(targetId: NodeId): Future[NodeInfo] = ???

  override def search(key: PartialKey[V]): Future[Seq[IndexEntry.Value[V]]] = ???

  override def putLocal(id: NodeId, value: Either[NodeInfo, IndexEntry.Value[V]]): Boolean = ???

  private object BroadcastEventHandler extends EventHandler[V] {

    private def broadcast(localNodeInfo: NodeInfo, lastHopPeer: NodeInfo, event: RequestEvent[V]): Unit = {
      Await.result(
        Future.sequence(
          peers.values
            .to(LazyList)
            .filterNot { peer => peer.id == lastHopPeer.id }
            .flatMap { peer =>
              event.forward(localNodeInfo, lastHopPeer).map { forwardEvent =>
                network.send(peer.address, forwardEvent).transform {
                  case Failure(exception) => Success(logger.warn("Failed to forward ping {} to {}", forwardEvent, peer))
                  case Success(value)     => Success(logger.info("Successfully forwarded ping {} to {}", forwardEvent, peer))
                }
              }
            },
        ),
        5.seconds,
      )
    }

    override def handlePing(request: PingEvent[V]): Either[RedirectEvent[V], PingAnswerEvent[V]] = request match
      case PingEvent(_, _, targetId, _) if targetId == localNodeInfo.id => request.createAnswer(true)
      case PingEvent(_, _, targetId, _) if peers.contains(targetId)     => request.createRedirect(localNodeInfo, peers(targetId))
      case event @ PingEvent(_, _, targetId, RoutingInfo(lastHopPeer, _, _)) =>
        broadcast(localNodeInfo, lastHopPeer, event)
        request.createAnswer(false)

    override def handleFindNode(request: FindNodeEvent[V]): Either[RedirectEvent[V], FindNodeAnswerEvent[V]] = request match
      case FindNodeEvent(_, _, targetId, _) if targetId == localNodeInfo.id => request.createAnswer(true)
      case FindNodeEvent(_, _, targetId, _) if peers.contains(targetId)     => request.createRedirect(localNodeInfo, peers(targetId))
      case event @ FindNodeEvent(_, _, targetId, RoutingInfo(lastHopPeer, _, _)) =>
        broadcast(localNodeInfo, lastHopPeer, event)
        request.createAnswer(false)

    override def handleSearch(request: SearchEvent[V]): Either[RedirectEvent[V], SearchAnswerEvent[V]] = {
      request.createAnswer(
        Some(
          values
            .range(request.search.startInclusive, request.search.endExclusive)
            .values
            .toSeq,
        ),
      )
    }

    override def handleStoreValue(request: StoreValueEvent[V]): Either[RedirectEvent[V], StoreValueAnswerEvent[V]] = request match
      case StoreValueEvent(_, _, targetId, _, value) if targetId == localNodeInfo.id =>
        values.put(value.value, value)
        request.createAnswer(true)
      case StoreValueEvent(_, _, targetId, _, _) if peers.contains(targetId) => request.createRedirect(localNodeInfo, peers(targetId))
      case event @ StoreValueEvent(_, _, targetId, RoutingInfo(lastHopPeer, _, _), _) =>
        broadcast(localNodeInfo, lastHopPeer, event)
        request.createAnswer(false)
  }
}
