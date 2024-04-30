package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class FindValueAnswerEvent[V](override val id: UUID, override val targetId: NodeId, value: Option[V]) extends AnswerEvent {
  override val found: Boolean = value.isDefined
  override val responseCode: Int = 200
}

object FindValueAnswerEvent extends AnswerEvent.ParameterizedFactory[FindValueAnswerEvent] {
  override val name: String = "find-value-answer"
  override def create[V](id: UUID, targetId: NodeId, value: Option[V]): FindValueAnswerEvent[V] =
    new FindValueAnswerEvent[V](id, targetId, value)
}
