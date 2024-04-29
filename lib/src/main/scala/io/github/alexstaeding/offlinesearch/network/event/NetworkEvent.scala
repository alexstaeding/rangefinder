package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID

trait NetworkEvent {
  val id: UUID
}

object NetworkEvent {
  trait Factory {
    val name: String
  }

  trait SimpleFactory[N <: NetworkEvent] extends Factory {
    given codec: JsonValueCodec[N] = JsonCodecMaker.make
  }

  trait ParameterizedFactory[N[_] <: NetworkEvent] extends Factory {
    given codec[V](using JsonValueCodec[V]): JsonValueCodec[N[V]] = JsonCodecMaker.make
  }
}
