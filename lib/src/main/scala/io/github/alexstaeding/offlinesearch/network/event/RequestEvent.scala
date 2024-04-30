package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

trait RequestEvent extends NetworkEvent {
  val targetId: NodeId
}

object RequestEvent {
  trait Factory extends NetworkEvent.Factory {
    val answerName: String
  }

  trait SimpleFactory[N <: NetworkEvent] extends NetworkEvent.SimpleFactory[N], Factory {
    def create(id: UUID, targetId: NodeId): N
  }

  trait ParameterizedFactory[N[_] <: NetworkEvent] extends NetworkEvent.ParameterizedFactory[N], Factory {
    def create[V](id: UUID, targetId: NodeId, value: Option[V]): N[V]
  }
}
