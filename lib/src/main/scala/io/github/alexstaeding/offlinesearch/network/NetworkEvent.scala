package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.util.UUID

/** Represents a message sent by or received from a [[NetworkAdapter]].
  */
sealed trait NetworkEvent[V] {

  /** Persistent ID for all messages in this communication
    */
  val requestId: UUID
}

sealed trait AnswerEvent[V] extends NetworkEvent[V]
sealed trait RequestEvent[V] extends NetworkEvent[V] {

  /** The ID of the node that sent this message
    */
  val sourceInfo: NodeInfo

  /** The ID of the node that being searched
    */
  val targetId: NodeId
}

object NetworkEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[NetworkEvent[V]] = JsonCodecMaker.make
}

object AnswerEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[AnswerEvent[V]] = JsonCodecMaker.make
}

object RequestEvent {
  given codec[V: JsonValueCodec]: JsonValueCodec[RequestEvent[V]] = JsonCodecMaker.make
}

case class PingEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
) extends RequestEvent[V]

case class FindNodeEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
) extends RequestEvent[V]

case class FindValueEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
) extends RequestEvent[V]

case class StoreValueEvent[V](
    override val requestId: UUID,
    override val sourceInfo: NodeInfo,
    override val targetId: NodeId,
    value: V,
) extends RequestEvent[V]

case class PingAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]
case class FindNodeAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]
case class FindValueAnswerEvent[V](override val requestId: UUID, value: Option[V]) extends AnswerEvent[V]
case class StoreValueAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]

case class RedirectEvent[V](override val requestId: UUID, closerTargetInfo: NodeInfo) extends AnswerEvent[V]
