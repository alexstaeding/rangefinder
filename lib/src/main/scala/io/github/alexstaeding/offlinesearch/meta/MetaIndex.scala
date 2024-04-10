package io.github.alexstaeding.offlinesearch.meta

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.NodeId
import io.github.alexstaeding.offlinesearch.crdt.{Lattice, NodeScoped, ReplicatedLWW}

case class MetaIndex[T](data: NodeScoped[ReplicatedLWW[Seq[PartialKey[T]]]])

object MetaIndex {
  given lattice[T]: Lattice[MetaIndex[T]] = { (left, right) =>
    import ReplicatedLWW.lattice
    MetaIndex(Lattice.mapLattice.merge(left.data, right.data))
  }

  given codec[T](using JsonValueCodec[T]): JsonValueCodec[MetaIndex[T]] = JsonCodecMaker.make
}

extension [T](metaIndex: MetaIndex[T])(using PartialKeyActions[T]) {

  /** Finds known partial keys that represent a partial T.
    *
    * e.g. the search "foobar" may return the partial key for "fo"
    */
  def getMatchingPartialKeys(search: T): Iterable[PartialKey[T]] = {
    // TODO: Optimize lookup, this does not change often
    metaIndex.data.values.flatMap(_.value).filter(_.matches(search))
  }
}

def foo(x: MetaIndex[String]): Unit = {
  import MetaIndex.codec

}
