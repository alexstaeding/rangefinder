package io.github.alexstaeding.offlinesearch.meta

trait PartialKeyUniverse[V] {
  def getRootPartialKey(value: V): PartialKey[V]
}
