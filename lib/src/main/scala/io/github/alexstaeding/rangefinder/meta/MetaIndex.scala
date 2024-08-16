package io.github.alexstaeding.rangefinder.meta

import io.github.alexstaeding.rangefinder.crdt.{Lattice, NodeScoped, ReplicatedLWW}

case class MetaIndex[T](data: NodeScoped[ReplicatedLWW[Seq[PartialKey[T]]]])

object MetaIndex {
  given lattice[T]: Lattice[MetaIndex[T]] = { (left, right) =>
    import ReplicatedLWW.lattice
    MetaIndex(Lattice.mapLattice.merge(left.data, right.data))
  }

//  given codec[V](using NodeIdSpace, JsonValueCodec[V]): JsonValueCodec[MetaIndex[V]] = JsonCodecMaker.make
}

extension [T](metaIndex: MetaIndex[T])(using PartialKeyMatcher[T]) {

  /** Finds known partial keys that represent a partial T.
    *
    * e.g. the search "foobar" may return the partial key for "fo"
    */
  def getMatchingPartialKeys(search: T): Iterable[PartialKey[T]] = {
    // TODO: Optimize lookup, this does not change often
    metaIndex.data.values.flatMap(_.value).filter(_.matches(search))
  }
}
