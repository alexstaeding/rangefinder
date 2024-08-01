package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.meta.PartialKey

import java.util.UUID

/** Represents a message sent by or received from a [[NetworkAdapter]].
  */
sealed trait NetworkEvent[V] {

  /** Persistent ID for all messages in this communication
    */
  val requestId: UUID

  /** The direct peer this message is meant for. Used to ensure that the sender has the correct ID for the target address.
    */
  val nextHopPeer: NodeInfo
}

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

  type Answer <: AnswerEvent[V]

  /** The ID of the node that sent this message
    */
  val sourceInfo: NodeInfo

  /** The ID of the node that being searched
    */
  val targetId: NodeId

  def createRedirect(closerTargetInfo: NodeInfo): Left[RedirectEvent[V], Answer] =
    Left(RedirectEvent(requestId, sourceInfo, closerTargetInfo))
}

object NetworkEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[NetworkEvent[V]] = JsonCodecMaker.make
}

object AnswerEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[AnswerEvent[V]] = JsonCodecMaker.make
}

object RequestEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[RequestEvent[V]] = JsonCodecMaker.make

  def createPing[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo): PingEvent[V] =
    PingEvent[V](UUID.randomUUID(), localNodeInfo, targetId, nextHopPeer)

  def createFindNode[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo): FindNodeEvent[V] =
    FindNodeEvent[V](UUID.randomUUID(), localNodeInfo, targetId, nextHopPeer)

  def createSearch[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, key: PartialKey[V]): SearchEvent[V] =
    SearchEvent[V](UUID.randomUUID(), localNodeInfo, targetId, nextHopPeer, key)

  def createStoreValue[V](localNodeInfo: NodeInfo, targetId: NodeId, nextHopPeer: NodeInfo, value: OwnedValue[V]): StoreValueEvent[V] =
    StoreValueEvent[V](UUID.randomUUID(), localNodeInfo, targetId, nextHopPeer, value)
}

case class PingEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val nextHopPeer: NodeInfo,
) extends RequestEvent[V] {
  override type Answer = PingAnswerEvent[V]
  def createAnswer(content: Boolean): Right[RedirectEvent[V], PingAnswerEvent[V]] =
    Right(PingAnswerEvent(requestId, sourceInfo, content))
}

case class FindNodeEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val nextHopPeer: NodeInfo,
) extends RequestEvent[V] {
  override type Answer = FindNodeAnswerEvent[V]
  def createAnswer(content: Boolean): Right[RedirectEvent[V], FindNodeAnswerEvent[V]] =
    Right(FindNodeAnswerEvent(requestId, sourceInfo, content))
}

case class SearchEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val nextHopPeer: NodeInfo,
    search: PartialKey[V],
) extends RequestEvent[V] {
  override type Answer = SearchAnswerEvent[V]
  def createAnswer(content: Option[Seq[OwnedValue[V]]]): Right[RedirectEvent[V], SearchAnswerEvent[V]] =
    Right(SearchAnswerEvent(requestId, sourceInfo, content))
}

case class StoreValueEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    override val nextHopPeer: NodeInfo,
    value: OwnedValue[V],
) extends RequestEvent[V] {
  override type Answer = StoreValueAnswerEvent[V]
  def createAnswer(content: Boolean): Right[RedirectEvent[V], StoreValueAnswerEvent[V]] =
    Right(StoreValueAnswerEvent(requestId, sourceInfo, content))
}

case class PingAnswerEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class FindNodeAnswerEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class SearchAnswerEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    override val content: Option[Seq[OwnedValue[V]]],
) extends SeqAnswerEvent[V]

case class StoreValueAnswerEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    override val content: Boolean,
) extends BooleanAnswerEvent[V]

case class RedirectEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    closerTargetInfo: NodeInfo,
) extends AnswerEvent[V] {
  override type Content = Nothing
  override def content: Nothing = throw new NoSuchElementException
}

case class ErrorEvent[V](
    override val requestId: UUID,
    override val nextHopPeer: NodeInfo,
    message: String,
) extends AnswerEvent[V] {
  override type Content = String
  override val content: String = message
}
