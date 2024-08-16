package io.github.alexstaeding.rangefinder.meta

trait PartialKeyUniverse[V] {
  def getRootKey(value: V): PartialKey[V]
  def getOverlappingRootKeys(key: PartialKey[V]): Seq[PartialKey[V]]
}
