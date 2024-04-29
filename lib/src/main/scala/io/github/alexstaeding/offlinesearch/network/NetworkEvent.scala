package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

sealed trait NetworkEvent {
  val id: UUID
}

sealed trait OutgoingRequest extends NetworkEvent {
  val targetId: NodeId
}

// TODO: sealed trait Answer extends NetworkEvent with signing?

object NetworkEvent {
  trait Factory[N <: NetworkEvent](val name: String) {
    given codec: JsonValueCodec[N] = JsonCodecMaker.make
    def create(id: UUID, targetId: NodeId): N
  }
  trait ParameterizedFactory[N[_] <: NetworkEvent](val name: String) {
    given codec[V](using JsonValueCodec[V]): JsonValueCodec[N[V]] = JsonCodecMaker.make
    def create[V](id: UUID, targetId: NodeId, value: Option[V]): N[V]
  }
}

case class PingEvent(override val id: UUID, override val targetId: NodeId) extends OutgoingRequest
object PingEvent extends NetworkEvent.Factory[PingEvent]("ping") {
  override def create(id: UUID, targetId: NodeId): PingEvent =
    new PingEvent(id, targetId)
}

case class PingAnswerEvent(override val id: UUID) extends NetworkEvent
object PingAnswerEvent extends NetworkEvent.Factory[PingAnswerEvent]("ping-answer") {
  override def create(id: UUID, targetId: NodeId): PingAnswerEvent =
    new PingAnswerEvent(id)
}

case class StoreValueEvent[V](override val id: UUID, override val targetId: NodeId, value: V) extends OutgoingRequest
object StoreValueEvent extends NetworkEvent.ParameterizedFactory[StoreValueEvent]("store-value") {
  override def create[V](id: UUID, targetId: NodeId, value: V): StoreValueEvent[V] =
    new StoreValueEvent[V](id, targetId, value)
}

case class StoreValueAnswerEvent(override val id: UUID) extends NetworkEvent
object StoreValueAnswerEvent extends NetworkEvent.Factory[StoreValueAnswerEvent]("store-value-answer") {
  override def create(id: UUID): StoreValueAnswerEvent =
    new StoreValueAnswerEvent(id)
}

case class FindNodeEvent(override val id: UUID, override val targetId: NodeId) extends OutgoingRequest
object FindNodeEvent extends NetworkEvent.Factory[FindNodeEvent]("find-node") {
  override def create(id: UUID, targetId: NodeId): FindNodeEvent =
    new FindNodeEvent(id, targetId)
}

case class FindNodeAnswerEvent(override val id: UUID) extends NetworkEvent
object FindNodeAnswerEvent extends NetworkEvent.Factory[FindNodeAnswerEvent]("find-node-answer")

case class FindValueEvent(override val id: UUID, override val targetId: NodeId) extends OutgoingRequest
object FindValueEvent extends NetworkEvent.Factory[FindValueEvent]("find-value") {
  override def create(id: UUID, targetId: NodeId): FindValueEvent =
    new FindValueEvent(id, targetId)
}

case class FindValueAnswerEvent[V](override val id: UUID, targetId: NodeId, value: Option[V]) extends NetworkEvent
object FindValueAnswerEvent extends NetworkEvent.ParameterizedFactory[FindValueAnswerEvent]("find-value-answer") {
  override def create[V](id: UUID, targetId: NodeId, value: Option[V]): FindValueAnswerEvent[V] =
    new FindValueAnswerEvent[V](id, targetId, value)
}

case class RedirectEvent(override val id: UUID, closerTargetId: NodeId) extends NetworkEvent
object RedirectEvent extends NetworkEvent.Factory[RedirectEvent]("redirect") {
  override def create(id: UUID, targetId: NodeId): RedirectEvent =
    new RedirectEvent(id, targetId)
}
