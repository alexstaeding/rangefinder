package io.github.alexstaeding.offlinesearch.crdt

import scala.math.*
import io.github.alexstaeding.offlinesearch.model.Counter

object CounterActions {
  def zero = Counter(0, 0)

  given lattice: Lattice[Counter] = { (left: Counter, right: Counter) =>
    
    left.toByteArray
    
    
    Counter(max(left.pos, right.pos), max(left.neg, right.neg))
  }

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
