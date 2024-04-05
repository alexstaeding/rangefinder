package io.github.alexstaeding.offlinesearch.crdt

case class ReplicatedLWW[T](generation: Long, value: T)

object ReplicatedLWW {
  given lattice[T]: Lattice[ReplicatedLWW[T]] = { (left, right) =>
    if (left.generation > right.generation) left else right
  }
}
