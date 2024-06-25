package io.github.alexstaeding.offlinesearch.meta

import scala.math.Ordered.orderingToOrdered

trait PartialKeyMatcher[V] {
  extension (partialKey: PartialKey[V]) {
    def matches(search: V): Boolean
  }
}

class OrderingPartialKeyMatcher[V: Ordering] extends PartialKeyMatcher[V] {
  extension (partialKey: PartialKey[V])
    override def matches(search: V): Boolean =
      partialKey.startInclusive <= search && search < partialKey.endExclusive
}
