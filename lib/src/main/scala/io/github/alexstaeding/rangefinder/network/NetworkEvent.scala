package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.rangefinder.meta.PartialKey

import java.util.UUID

/** Represents a message sent by or received from a [[NetworkAdapter]].
  */
sealed trait NetworkEvent[V] {

  /** Persistent ID for all messages in this communication
    */
  val requestId: UUID

  val routingInfo: RoutingInfo
}

case class RoutingInfo(
    /** The direct peer this message came from.
      */
    lastHopPeer: NodeInfo,

    /** The direct peer this message is meant for. Used to ensure that the sender has the correct ID for the target address.
      */
    nextHopPeer: NodeInfo,

    /** The number of hops left for this packet. If a packet with ttl = 1 arrives, do not forward.
      */
    ttl: Int,
)

object RoutingInfo {
  def direct(lastHopPeer: NodeInfo, nextHopPeer: NodeInfo): RoutingInfo = RoutingInfo(lastHopPeer, nextHopPeer, 1)
}

type RedirectOr[V, A <: AnswerEvent[V]] = Either[RedirectEvent[V], A]

sealed trait AnswerEvent[V] extends NetworkEvent[V] {
  type Content
  def content: Content
}

sealed trait SeqAnswerEvent[V] extends AnswerEvent[V] {
  override type Content = Option[Seq[OwnedValue[V]]]
}

sealed trait BooleanAnswerEvent[V] extends AnswerEvent[V] {
  override type Content = Boolean
}

sealed trait RequestEvent[V] extends NetworkEvent[V] {

  type This <: RequestEvent[V]
  type Answer <: AnswerEvent[V]

  /** The ID of the node that sent this message
    */
  val sourceInfo: NodeInfo

  /** The ID of the node that being searched
    */
  val targetId: NodeId

  def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[This]

  def createRedirect(localNodeInfo: NodeInfo, closerTargetInfo: NodeInfo): Left[RedirectEvent[V], Answer] =
    Left(RedirectEvent(requestId, RoutingInfo.direct(localNodeInfo, sourceInfo), closerTargetInfo))

  def createError(message: String): ErrorEvent[V] = ErrorEvent(requestId, routingInfo, message)
}

object NetworkEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[NetworkEvent[V]] = JsonCodecMaker.make
}

object AnswerEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[AnswerEvent[V]] = JsonCodecMaker.make
}

object RequestEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[RequestEvent[V]] = JsonCodecMaker.make

  def createPing[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, ttl: Int = 1): PingEvent[V] =
    PingEvent[V](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl))

  def createFindNode[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, ttl: Int = 1): FindNodeEvent[V] =
    FindNodeEvent[V](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl))

  def createSearch[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, key: PartialKey[V], ttl: Int = 1): SearchEvent[V] =
    SearchEvent[V](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl), key)

  def createStoreValue[V](
      localNodeInfo: NodeInfo,
      targetId: NodeId,
      nextHopPeer: NodeInfo,
      value: OwnedValue[V],
      ttl: Int = 1,
  ): StoreValueEvent[V] =
    StoreValueEvent[V](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl), value)
}

final case class PingEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
) extends RequestEvent[V] {
  override type This = PingEvent[V]
  override type Answer = PingAnswerEvent[V]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[PingEvent[V]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Boolean): Right[RedirectEvent[V], PingAnswerEvent[V]] =
    Right(PingAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class FindNodeEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
) extends RequestEvent[V] {
  override type This = FindNodeEvent[V]
  override type Answer = FindNodeAnswerEvent[V]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[FindNodeEvent[V]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Boolean): Right[RedirectEvent[V], FindNodeAnswerEvent[V]] =
    Right(FindNodeAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class SearchEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
    search: PartialKey[V],
) extends RequestEvent[V] {
  override type This = SearchEvent[V]
  override type Answer = SearchAnswerEvent[V]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[SearchEvent[V]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Option[Seq[OwnedValue[V]]]): Right[RedirectEvent[V], SearchAnswerEvent[V]] =
    Right(SearchAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class StoreValueEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
    value: OwnedValue[V],
) extends RequestEvent[V] {
  override type This = StoreValueEvent[V]
  override type Answer = StoreValueAnswerEvent[V]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[StoreValueEvent[V]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Boolean): Right[RedirectEvent[V], StoreValueAnswerEvent[V]] =
    Right(StoreValueAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class PingAnswerEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class FindNodeAnswerEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class SearchAnswerEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Option[Seq[OwnedValue[V]]],
) extends SeqAnswerEvent[V]

case class StoreValueAnswerEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class RedirectEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    closerTargetInfo: NodeInfo,
) extends AnswerEvent[V] {
  override type Content = Nothing
  override def content: Nothing = throw new NoSuchElementException
}

case class ErrorEvent[V](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    message: String,
) extends AnswerEvent[V] {
  override type Content = String
  override val content: String = message
}

extension [V, A <: AnswerEvent[V]](answer: A) {
  def extractRedirect(): RedirectOr[V, A] = {
    answer match
      case redirect: RedirectEvent[V] => Left(redirect)
      case answer =>
        answer match
          case ErrorEvent(_, _, message) => throw new RuntimeException(message)
          case _                         => Right(answer)
  }
}
