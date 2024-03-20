package io.github.alexstaeding.crdt

import scala.math.*

case class ReplicatedCounter(pos: Int, neg: Int = 0)

object ReplicatedCounter {
  def zero: ReplicatedCounter = ReplicatedCounter(0, 0)

  given lattice: Lattice[ReplicatedCounter] = {
    (left: ReplicatedCounter, right: ReplicatedCounter) => ReplicatedCounter(max(left.pos, right.pos), max(left.neg, right.neg))
  }

  extension [C <: ReplicatedCounter](container: C) {
    def value: Int = container.pos - container.neg
    def add(amount: Int): ReplicatedCounter = {
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
