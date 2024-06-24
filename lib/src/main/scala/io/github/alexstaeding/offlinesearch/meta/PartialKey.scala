package io.github.alexstaeding.offlinesearch.meta

/** A partial T represents a range of Ts.
  */
case class PartialKey[V](startInclusive: V, endExclusive: V, parent: Option[PartialKey[V]]) {
  val isRoot: Boolean = parent.isEmpty
}
