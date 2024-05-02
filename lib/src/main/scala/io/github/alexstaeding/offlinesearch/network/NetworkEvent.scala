package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.util.UUID

sealed trait NetworkEvent[V] {

  /** Persistent ID for all messages in this communication
    */
  val requestId: UUID
}

sealed trait AnswerEvent[V] extends NetworkEvent[V]
sealed trait RequestEvent[V] extends NetworkEvent[V] {
  val targetId: NodeId
}

trait EventCodecFactory[E[V] <: NetworkEvent[V]] {
  given codec[V](using JsonValueCodec[V]): JsonValueCodec[E[V]] = JsonCodecMaker.make
}

object NetworkEvent extends EventCodecFactory[NetworkEvent]
object AnswerEvent extends EventCodecFactory[AnswerEvent]
object RequestEvent extends EventCodecFactory[RequestEvent]

case class PingEvent[V](override val requestId: UUID, override val targetId: NodeId) extends RequestEvent[V]
case class FindNodeEvent[V](override val requestId: UUID, override val targetId: NodeId) extends RequestEvent[V]
case class FindValueEvent[V](override val requestId: UUID, override val targetId: NodeId) extends RequestEvent[V]
case class StoreValueEvent[V](override val requestId: UUID, override val targetId: NodeId, value: V) extends RequestEvent[V]

case class PingAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]
case class FindNodeAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]
case class FindValueAnswerEvent[V](override val requestId: UUID, value: Option[V]) extends AnswerEvent[V]
case class StoreValueAnswerEvent[V](override val requestId: UUID, success: Boolean) extends AnswerEvent[V]

case class RedirectEvent[V](override val requestId: UUID, closerTargetInfo: NodeInfo) extends AnswerEvent[V]
