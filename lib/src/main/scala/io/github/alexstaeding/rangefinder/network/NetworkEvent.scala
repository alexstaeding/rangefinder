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

type RedirectOr[A <: AnswerEvent[?, ?]] = Either[RedirectEvent, A]

sealed trait AnswerEvent[+V, +P] extends NetworkEvent[V, P] {
  type Content
  def content: Content
}

sealed trait SeqAnswerEvent[V, P] extends AnswerEvent[V, P] {
  override type Content = Option[Seq[IndexEntry[V, P]]]
}

sealed trait BooleanAnswerEvent[V] extends AnswerEvent[V, Nothing] {
  override type Content = Boolean
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

  def createRedirect(localNodeInfo: NodeInfo, closerTargetInfo: NodeInfo): Left[RedirectEvent, Answer] =
    Left(RedirectEvent(requestId, RoutingInfo.direct(localNodeInfo, sourceInfo), closerTargetInfo))

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
  def createAnswer(content: Boolean): Right[RedirectEvent, PingAnswerEvent] =
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
  def createAnswer(content: Boolean): Right[RedirectEvent, FindNodeAnswerEvent] =
    Right(FindNodeAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class SearchEvent[V, P](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val routingInfo: RoutingInfo,
    search: PartialKey[V],
) extends RequestEvent[V, P] {
  override type This = SearchEvent[V, P]
  override type Answer = SearchAnswerEvent[V, P]
  override def forward(localNodeInfo: NodeInfo, nextHopPeer: NodeInfo): Option[SearchEvent[V, P]] =
    if (routingInfo.ttl <= 1) None else Some(copy(routingInfo = RoutingInfo(localNodeInfo, nextHopPeer, routingInfo.ttl - 1)))
  def createAnswer(content: Option[Seq[IndexEntry[V, P]]]): Right[RedirectEvent, SearchAnswerEvent[V, P]] =
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
  def createAnswer(content: Boolean): Right[RedirectEvent, StoreValueAnswerEvent] =
    Right(StoreValueAnswerEvent(requestId, RoutingInfo(routingInfo.nextHopPeer, sourceInfo, 1), content))
}

case class PingAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[Nothing]

case class FindNodeAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[Nothing]

case class SearchAnswerEvent[V, P](
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Option[Seq[IndexEntry[V, P]]],
) extends SeqAnswerEvent[V, P]

case class StoreValueAnswerEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[Nothing]

case class RedirectEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    closerTargetInfo: NodeInfo,
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = Nothing
  override def content: Nothing = throw new NoSuchElementException
}

case class ErrorEvent(
    override val requestId: UUID,
    override val routingInfo: RoutingInfo,
    message: String,
) extends AnswerEvent[Nothing, Nothing] {
  override type Content = String
  override val content: String = message
}

extension [A <: AnswerEvent[?, ?]](answer: A) {
  def extractRedirect(): RedirectOr[A] = {
    answer match
      case redirect: RedirectEvent => Left(redirect)
      case answer =>
        answer match
          case ErrorEvent(_, _, message) => throw new RuntimeException(message)
          case _                         => Right(answer)
  }
}
