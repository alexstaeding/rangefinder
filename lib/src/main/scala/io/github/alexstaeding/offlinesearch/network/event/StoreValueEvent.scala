package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class StoreValueEvent[V](override val id: UUID, override val targetId: NodeId, value: V) extends RequestEvent

object StoreValueEvent extends RequestEvent.ParameterizedFactory[StoreValueEvent] {
  override val name: String = "store-value"
  override def create[V](id: UUID, targetId: NodeId, value: V): StoreValueEvent[V] =
    new StoreValueEvent[V](id, targetId, value)
}
