package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.NodeInfo

import java.util.UUID

case class RedirectEvent(override val id: UUID, closerTargetInfo: NodeInfo) extends AnswerEvent

object RedirectEvent extends NetworkEvent.Factory {
  override val name: String = "redirect"
  given codec: JsonValueCodec[NotFoundEvent] = JsonCodecMaker.make
}
