package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.util.UUID

// TODO: signing?
trait AnswerEvent extends NetworkEvent {
  val responseCode: Int // TODO: Do this better
}

object AnswerEvent {
  trait SimpleFactory[N <: AnswerEvent] extends NetworkEvent.SimpleFactory[N] {
    def create(id: UUID): N
  }

  trait ParameterizedFactory[N[_] <: AnswerEvent] extends NetworkEvent.ParameterizedFactory[N] {
    def create[V](id: UUID, value: Option[V]): N[V]
  }
}
