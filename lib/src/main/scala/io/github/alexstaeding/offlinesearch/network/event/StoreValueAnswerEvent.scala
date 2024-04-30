package io.github.alexstaeding.offlinesearch.network.event

import java.util.UUID

case class StoreValueAnswerEvent(override val id: UUID) extends AnswerEvent {
  
}

object StoreValueAnswerEvent extends AnswerEvent.Factory[StoreValueAnswerEvent] {
  override val name: String = "store-value-answer"
  override def create(id: UUID): StoreValueAnswerEvent = new StoreValueAnswerEvent(id)
}
