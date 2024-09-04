package io.github.alexstaeding.rangefinder.meta

trait PartialKeyUniverse[V] {
  def getRootKey(value: V): PartialKey[V]
  def getOverlappingRootKeys(key: PartialKey[V]): Seq[PartialKey[V]]
}

extension [V](universe: PartialKeyUniverse[V]) {
  def map[U](apply: V => U, unapply: U => V): PartialKeyUniverse[U] = new PartialKeyUniverse[U] {
    override def getRootKey(value: U): PartialKey[U] =
      universe.getRootKey(unapply(value)).map(apply)
    override def getOverlappingRootKeys(key: PartialKey[U]): Seq[PartialKey[U]] =
      universe.getOverlappingRootKeys(key.map(unapply)).map(_.map(apply))
  }
}
