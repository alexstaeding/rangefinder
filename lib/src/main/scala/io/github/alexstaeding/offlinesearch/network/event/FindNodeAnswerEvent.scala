package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class FindNodeAnswerEvent[V](override val id: UUID) extends AnswerEvent[V] {
  override val responseCode: Int = 200
}

object FindNodeAnswerEvent {
  given factory[V]: AnswerEvent.Factory[FindNodeAnswerEvent[V], V] = new AnswerEvent.Factory[FindNodeAnswerEvent[V], V] {
    override val name: String = "find-node-answer"
    override def create(id: UUID): FindNodeAnswerEvent[V] = new FindNodeAnswerEvent(id)
  }
}
