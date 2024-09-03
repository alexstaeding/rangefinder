package io.github.alexstaeding.rangefinder.crdt

import scala.annotation.targetName

@FunctionalInterface
trait Lattice[V] {

  def merge(left: V, right: V): V

  extension (left: V) {
    @targetName("mergeInfix")
    inline infix def merge(right: V): V = Lattice.this.merge(left, right)
  }
}

object Lattice {

  given mapLattice[K, V: Lattice]: Lattice[Map[K, V]] = (left: Map[K, V], right: Map[K, V]) =>
    val (small, large) =
      // compare unsigned treats the “unknown” entry -1 as larger than any known size
      if 0 <= Integer.compareUnsigned(left.knownSize, right.knownSize)
      then (right, left)
      else (left, right)
    small.foldLeft(large) { case (current, (key, r)) =>
      current.updatedWith(key) {
        case Some(l) => Some(l merge r)
        case None    => Some(r)
      }
    }

  given setLattice[A]: Lattice[Set[A]] with {
    override def merge(left: Set[A], right: Set[A]): Set[A] = left union right
  }
}
