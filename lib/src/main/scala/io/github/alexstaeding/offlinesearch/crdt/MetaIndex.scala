package io.github.alexstaeding.offlinesearch.crdt

import java.util.UUID

trait MetaIndex[T] {
  /**
    * Finds known partial keys that represent a partial T.
    * 
    * e.g. the search "foobar" may return the partial key for "fo"
    *
    * @param search
    * @return
    */
  def getIndexGroups(search: T): List[? <: PartialKey[T]]
}
