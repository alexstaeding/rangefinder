package io.github.alexstaeding.offlinesearch.network.event

import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

case class PingEvent(override val id: UUID, override val targetId: NodeId) extends RequestEvent

object PingEvent extends RequestEvent.SimpleFactory[PingEvent] {
  override val name: String = "ping"
  override def create(id: UUID, targetId: NodeId): PingEvent =
    new PingEvent(id, targetId)
}
