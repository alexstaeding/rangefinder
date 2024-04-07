package io.github.alexstaeding.offlinesearch.meta

/** A partial T that represents a cluster of Ts that are "close enough"
  * together.
  */
case class PartialKey[T](value: T)

trait PartialKeyActions[T] {
  extension (partialKey: PartialKey[T]) {
    def matches(search: T): Boolean
    def ring(degree: Int): PartialKeyRing[T]
  }
}
