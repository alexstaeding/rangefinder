package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.util.UUID

// TODO: signing?
trait AnswerEvent[V] extends NetworkEvent[V] {
  val responseCode: Int // TODO: Do this better
}

object AnswerEvent {
  trait Factory[A <: AnswerEvent[V], V] extends NetworkEvent.Factory[A, V]
}
