package io.github.alexstaeding.rangefinder.crdt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.math.max

case class ReplicatedBoolean(private val counter: Long)

object ReplicatedBoolean {
  def createFalse: ReplicatedBoolean = ReplicatedBoolean(0)
  def createTrue: ReplicatedBoolean = ReplicatedBoolean(1)

  given lattice: Lattice[ReplicatedBoolean] = { (left, right) =>
    ReplicatedBoolean(max(left.counter, right.counter))
  }

  given codec: JsonValueCodec[ReplicatedBoolean] = JsonCodecMaker.make

  given toBoolean: Conversion[ReplicatedBoolean, Boolean] = { _.value }

  extension (container: ReplicatedBoolean) {
    def value: Boolean = container.counter % 2 == 1
    def inverted: ReplicatedBoolean = ReplicatedBoolean(container.counter + 1)
    def toFalse: ReplicatedBoolean = if (value) inverted else container
    def toTrue: ReplicatedBoolean = if (!value) inverted else container
  }
}
