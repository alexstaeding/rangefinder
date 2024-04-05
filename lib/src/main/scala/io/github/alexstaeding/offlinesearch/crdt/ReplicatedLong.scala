package io.github.alexstaeding.offlinesearch.crdt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.math.max

case class ReplicatedLong(pos: Long, neg: Long)

object ReplicatedLong {
  def zero: ReplicatedLong = ReplicatedLong(0, 0)

  given lattice: Lattice[ReplicatedLong] = { (left: ReplicatedLong, right: ReplicatedLong) =>
    ReplicatedLong(max(left.pos, right.pos), max(left.neg, right.neg))
  }

  given codec: JsonValueCodec[ReplicatedLong] = JsonCodecMaker.make

  given toLong: Conversion[ReplicatedLong, Long] = { _.value }

  extension (container: ReplicatedLong) {
    def value: Long = container.pos - container.neg
    def add(amount: Long): ReplicatedLong = {
      if (amount > 0) {
        container.copy(pos = container.pos + amount)
      } else if (amount < 0) {
        container.copy(neg = container.neg + amount)
      } else {
        container
      }
    }
  }
}
