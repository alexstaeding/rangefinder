package io.github.alexstaeding.rangefinder.crdt

import java.time.OffsetDateTime
import scala.collection.immutable.{HashMap, SortedMap, TreeMap}

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

  given lattice[K: Ordering, E]: Lattice[SortedGrowOnlyExpiryMultiMap[K, E]] =
    Lattice
      .mapLattice(using GrowOnlyExpiryMap.lattice)
      .map(TreeMap.from)
}
