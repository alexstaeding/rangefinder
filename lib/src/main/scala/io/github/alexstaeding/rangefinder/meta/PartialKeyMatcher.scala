package io.github.alexstaeding.rangefinder.meta

import scala.math.Ordered.orderingToOrdered

trait PartialKeyMatcher[V] {
  extension (partialKey: PartialKey[V]) {
    def matches(search: V): Boolean
    def contains(searchKey: PartialKey[V]): Boolean
  }
}

class OrderingPartialKeyMatcher[V: Ordering] extends PartialKeyMatcher[V] {
  extension (partialKey: PartialKey[V]) {
    override def matches(search: V): Boolean =
      partialKey.startInclusive <= search && search < partialKey.endExclusive
    override def contains(searchKey: PartialKey[V]): Boolean =
      partialKey.startInclusive <= searchKey.startInclusive && searchKey.endExclusive <= partialKey.endExclusive
  }
}
