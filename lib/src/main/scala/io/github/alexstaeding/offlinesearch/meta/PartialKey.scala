package io.github.alexstaeding.offlinesearch.meta

/** A partial T represents a range of Ts.
  */
case class PartialKey[V](startInclusive: V, endExclusive: V) {
  def map[U](f: V => U): PartialKey[U] = PartialKey(f(startInclusive), f(endExclusive))
}

object PartialKey {
  def ofOne[V](value: V): PartialKey[V] = PartialKey(value, value)
}
