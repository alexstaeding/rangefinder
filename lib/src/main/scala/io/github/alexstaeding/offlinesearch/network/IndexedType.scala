package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.meta.{MetaIndex, ReplicationGroup}

/** T = the indexed type
  */
class IndexedType[T] {
  type MetaIndexType = MetaIndex[T]
  type IndexGroupType = ReplicationGroup[T]
}

def foo(): Unit = {}
