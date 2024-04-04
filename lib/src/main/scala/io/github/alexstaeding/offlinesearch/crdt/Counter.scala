package io.github.alexstaeding.offlinesearch.crdt

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.math.*

case class Counter(pos: Long, neg: Long)

object Counter {
  def zero = Counter(0, 0)

  given lattice: Lattice[Counter] = { (left: Counter, right: Counter) =>
    Counter(max(left.pos, right.pos), max(left.neg, right.neg))
  }

  given codec: JsonValueCodec[Counter] = JsonCodecMaker.make

  extension [C <: Counter](container: C) {
    def value: Long = container.pos - container.neg
    def add(amount: Long): Counter = {
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
