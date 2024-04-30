package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.NodeInfo

import java.util.UUID

case class NotFoundEvent(override val id: UUID) extends AnswerEvent {
  override val responseCode: Int = 404
}

object NotFoundEvent extends NetworkEvent.Factory {
  override val name: String = "not-found"
  given codec: JsonValueCodec[NotFoundEvent] = JsonCodecMaker.make
}
