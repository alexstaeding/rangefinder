package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class PingAnswerEvent(override val id: UUID) extends AnswerEvent {
  override val responseCode: Int = 200
}

object PingAnswerEvent extends AnswerEvent.Factory[PingAnswerEvent] {
  override val name: String = "ping-answer"
  override def create(id: UUID): PingAnswerEvent = new PingAnswerEvent(id)
}
