package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class FindNodeAnswerEvent(override val id: UUID) extends AnswerEvent {
  override val responseCode: Int = 200
}

object FindNodeAnswerEvent extends AnswerEvent.SimpleFactory[FindNodeAnswerEvent] {
  override val name: String = "find-node-answer"
  override def create(id: UUID): FindNodeAnswerEvent = new FindNodeAnswerEvent(id)
}
