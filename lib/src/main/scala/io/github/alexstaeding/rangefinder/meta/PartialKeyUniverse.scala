package io.github.alexstaeding.rangefinder.meta

trait PartialKeyUniverse[V] {
  def getIndexKeys(value: V): Seq[PartialKey[V]]
  def getIndexKeys(key: PartialKey[V]): Seq[PartialKey[V]]
}

extension [V](universe: PartialKeyUniverse[V]) {
  def map[U](apply: V => U, unapply: U => V): PartialKeyUniverse[U] = new PartialKeyUniverse[U] {
    override def getIndexKeys(value: U): Seq[PartialKey[U]] =
      universe.getIndexKeys(unapply(value)).map(_.map(apply))
    override def getIndexKeys(key: PartialKey[U]): Seq[PartialKey[U]] =
      universe.getIndexKeys(key.map(unapply)).map(_.map(apply))
  }
}
