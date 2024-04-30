package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.NodeId

import java.util.UUID
import scala.reflect.{ClassTag, classTag}

trait NetworkEvent[+V] {
  val id: UUID

  val value: EventResult[V]
}

object NetworkEvent {
  trait Factory[N <: NetworkEvent[V], V] {
    val name: String

    given codec: JsonValueCodec[N] = JsonCodecMaker.make

    def create(id: UUID, value: EventResult[V]): N
  }

  trait SomeFactory[N <: NetworkEvent[V], V] extends Factory[N, V] {
    def create(id: UUID, value: V): N
  }
}
