package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

trait RequestEvent[V] extends NetworkEvent[V] {
  val targetId: NodeId
}

object RequestEvent {
  trait Factory[R <: RequestEvent[V], V] extends NetworkEvent.Factory[R, V] {
    val answerName: String
  }
  trait SomeFactory[R <: RequestEvent[V], V] extends NetworkEvent.Factory[R, V], Factory[R, V] {
    
  }

  trait SimpleFactory[N <: NetworkEvent] extends NetworkEvent.SimpleFactory[N], Factory {
    def create(id: UUID, targetId: NodeId): N
  }

  trait ParameterizedFactory[N[_] <: NetworkEvent] extends NetworkEvent.ParameterizedFactory[N], Factory {
    def create[V](id: UUID, targetId: NodeId, value: Option[V]): N[V]
  }
}
