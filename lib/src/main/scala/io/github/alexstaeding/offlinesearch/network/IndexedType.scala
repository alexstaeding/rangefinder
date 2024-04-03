package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.crdt.MetaIndex
import io.github.alexstaeding.crdt.IndexGroup

/**
  * T = the indexed type
  */
class IndexedType[T] {
  type MetaIndexType = MetaIndex[T]
  type IndexGroupType = IndexGroup[T]
}

def foo(): Unit = {
}
