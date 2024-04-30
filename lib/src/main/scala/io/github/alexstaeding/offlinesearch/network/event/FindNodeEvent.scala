package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class FindNodeEvent(override val id: UUID, override val targetId: NodeId) extends RequestEvent

object FindNodeEvent extends RequestEvent.SimpleFactory[FindNodeEvent] {
  override val name: String = "find-node"
  override val answerName: String = "find-node-answer"
  override def create(id: UUID, targetId: NodeId): FindNodeEvent = new FindNodeEvent(id, targetId)
}
