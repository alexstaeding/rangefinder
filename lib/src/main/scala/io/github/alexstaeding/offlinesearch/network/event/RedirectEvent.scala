package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class RedirectEvent(override val id: UUID, closerTargetId: NodeId) extends AnswerEvent

object RedirectEvent extends AnswerEvent.SimpleFactory[RedirectEvent] {
  override val name: String = "redirect"
  override def create(id: UUID, targetId: NodeId): RedirectEvent =
    new RedirectEvent(id, targetId)
}
