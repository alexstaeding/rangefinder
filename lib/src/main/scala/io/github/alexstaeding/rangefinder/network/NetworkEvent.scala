package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.rangefinder.meta.PartialKey

import java.time.OffsetDateTime
import java.util.UUID

/** Represents a message sent by or received from a [[NetworkAdapter]].
  */
sealed trait NetworkEvent[+V, +P] {

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

sealed trait AnswerEvent[+V, +P] extends NetworkEvent[V, P] {
  type Content
  def content: Content
}

sealed trait RequestEvent[+V, +P] extends NetworkEvent[V, P] {

  type This <: RequestEvent[V, P]
  type Answer <: AnswerEvent[V, P]

  /** The ID of the node that sent this message
    */
  val sourceInfo: NodeInfo

  /** The ID of the node that being searched
    */
  val targetId: NodeId

  def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[This]

  def createError(message: String): ErrorEvent = ErrorEvent(requestId, routingInfo, message)
}

object NetworkEvent {
  given codec[V: JsonValueCodec, P: JsonValueCodec]: JsonValueCodec[NetworkEvent[V, P]] = JsonCodecMaker.make
}

object AnswerEvent {
  given codec[V: JsonValueCodec, P: JsonValueCodec]: JsonValueCodec[AnswerEvent[V, P]] = JsonCodecMaker.make
}

object RequestEvent {
  given codec[V: JsonValueCodec, P: JsonValueCodec]: JsonValueCodec[RequestEvent[V, P]] = JsonCodecMaker.make

  def createPing(localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, ttl: Int = 1): PingEvent =
    PingEvent(UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl))

  def createFindNode(localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, ttl: Int = 1): FindNodeEvent =
    FindNodeEvent(UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl))

  def createSearch[V, P](
      localNodeInfo: NodeInfo,
      targetId: NodeId,
      nextHopPeer: NodeInfo,
      key: PartialKey[V],
      ttl: Int = 1,
  ): SearchEvent[V, P] =
    SearchEvent[V, P](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl), key)

  def createStoreValue[V, P](
      localNodeInfo: NodeInfo,
      targetId: NodeId,
      nextHopPeer: NodeInfo,
      value: IndexEntry[V, P],
      expiration: OffsetDateTime = OffsetDateTime.now().plusHours(1),
      ttl: Int = 1,
  ): StoreValueEvent[V, P] =
    StoreValueEvent[V, P](UUID.randomUUID(), localNodeInfo, targetId, RoutingInfo(localNodeInfo, nextHopPeer, ttl), value, expiration)
}

final case class PingEvent(
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
) extends RequestEvent[Nothing, Nothing] {
  override type This = PingEvent
  override type Answer = PingAnswerEvent
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[PingEvent] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Boolean): Right[ErrorEvent, PingAnswerEvent] =
    Right(PingAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class FindNodeEvent(
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
) extends RequestEvent[Nothing, Nothing] {
  override type This = FindNodeEvent
  override type Answer = FindNodeAnswerEvent
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[FindNodeEvent] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Seq[NodeInfo]): Right[ErrorEvent, FindNodeAnswerEvent] =
    Right(FindNodeAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class SearchEvent[V, P](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
    searchKey: PartialKey[V],
) extends RequestEvent[V, P] {
  override type This = SearchEvent[V, P]
  override type Answer = SearchAnswerEvent[V, P]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[SearchEvent[V, P]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Seq[IndexEntry[V, P]]): Right[ErrorEvent, SearchAnswerEvent[V, P]] =
    Right(SearchAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class StoreValueEvent[V, P](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
    value: IndexEntry[V, P],
    expiration: OffsetDateTime,
) extends RequestEvent[V, P] {
  override type This = StoreValueEvent[V, P]
  override type Answer = StoreValueAnswerEvent
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[StoreValueEvent[V, P]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Boolean): Right[ErrorEvent, StoreValueAnswerEvent] =
    Right(StoreValueAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class PingAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = Boolean
}

case class FindNodeAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Seq[NodeInfo],
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = Seq[NodeInfo]
}

case class SearchAnswerEvent[V, P](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Seq[IndexEntry[V, P]],
) extends AnswerEvent[V, P] {
  override type Content = Seq[IndexEntry[V, P]]
}

case class StoreValueAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = this.type
}

case class ErrorEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    message: String,
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = String
  override val content: String = message
}
