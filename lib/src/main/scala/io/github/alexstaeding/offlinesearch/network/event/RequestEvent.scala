package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

trait RequestEvent extends NetworkEvent {
  val targetId: NodeId
}

object RequestEvent {
  trait SimpleFactory[N <: NetworkEvent] extends NetworkEvent.SimpleFactory[N] {
    def create(id: UUID, targetId: NodeId): N
  }

  trait ParameterizedFactory[N[_] <: NetworkEvent] extends NetworkEvent.ParameterizedFactory[N] {
    def create[V](id: UUID, targetId: NodeId, value: Option[V]): N[V]
  }
}
