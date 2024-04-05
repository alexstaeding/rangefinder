package io.github.alexstaeding.offlinesearch.crdt

import scala.annotation.targetName

@FunctionalInterface
trait Lattice[A] {

  def merge(left: A, right: A): A

  extension (left: A) {
    @targetName("mergeInfix")
    inline infix def merge(right: A): A = Lattice.this.merge(left, right)
  }
}

object Lattice {

  given mapLattice[K, V: Lattice]: Lattice[Map[K, V]] = (left: Map[K, V], right: Map[K, V]) =>
    val (small, large) =
      // compare unsigned treats the “unknown” value -1 as larger than any known size
      if 0 <= Integer.compareUnsigned(left.knownSize, right.knownSize)
      then (right, left)
      else (left, right)
    small.foldLeft(large) { case (current, (key, r)) =>
      current.updatedWith(key) {
        case Some(l) => Some(l merge r)
        case None    => Some(r)
      }
    }
}
