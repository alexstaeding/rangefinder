package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class PingAnswerEvent(override val id: UUID) extends AnswerEvent

object PingAnswerEvent extends AnswerEvent.SimpleFactory[PingAnswerEvent] {
  override val name: String = "ping-answer"
  override def create(id: UUID, targetId: NodeId): PingAnswerEvent =
    new PingAnswerEvent(id)
}
