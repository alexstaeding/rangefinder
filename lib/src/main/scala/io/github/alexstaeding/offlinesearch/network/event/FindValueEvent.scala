package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class FindValueEvent(override val id: UUID, override val targetId: NodeId) extends RequestEvent

object FindValueEvent extends RequestEvent.SimpleFactory[FindValueEvent] {
  override val name: String = "find-value"
  override val answerName: String = "find-value-answer"
  override def create(id: UUID, targetId: NodeId): FindValueEvent = new FindValueEvent(id, targetId)
}
