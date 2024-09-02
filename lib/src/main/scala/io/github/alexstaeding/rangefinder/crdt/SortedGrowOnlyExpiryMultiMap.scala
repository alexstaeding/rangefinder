package io.github.alexstaeding.rangefinder.crdt

import java.time.OffsetDateTime
import scala.annotation.targetName
import scala.collection.SortedMap
import scala.collection.immutable.{HashMap, TreeMap}
import scala.math.Ordering.Implicits.*
import scala.reflect.ClassTag

type SortedGrowOnlyExpiryMultiMap[K, E] = SortedMap[K, GrowOnlyExpiryMap[E]]

object SortedGrowOnlyExpiryMultiMap {

  def ofOne[K: Ordering, E](
      key: K,
      element: E,
      expiration: OffsetDateTime = OffsetDateTime.now().plusHours(1),
  ): SortedGrowOnlyExpiryMultiMap[K, E] =
    TreeMap(key -> GrowOnlyExpiryMap.ofOne(element, expiration))

  extension [K: Ordering, E](map: SortedGrowOnlyExpiryMultiMap[K, E]) {
    def cleaned(now: OffsetDateTime = OffsetDateTime.now()): SortedGrowOnlyExpiryMultiMap[K, E] = {
      map.map { (k, es) => k -> es.filter { (_, t) => t.isAfter(now) } }
    }
  }

  given lattice[K: Ordering, E]: Lattice[SortedGrowOnlyExpiryMultiMap[K, E]] with {
    override def merge(
        left: SortedGrowOnlyExpiryMultiMap[K, E],
        right: SortedGrowOnlyExpiryMultiMap[K, E],
    ): SortedGrowOnlyExpiryMultiMap[K, E] = {
      val now = OffsetDateTime.now()
      TreeMap.from(
        HashMap
          .from(left.cleaned(now))
          .merged(HashMap.from(right.cleaned(now))) { case ((key, leftItems), (_, rightItems)) =>
            // conflict resolution function for multiple index items with the same key
            key -> GrowOnlyExpiryMap.lattice.merge(leftItems, rightItems)
          },
      )
    }
  }
}
