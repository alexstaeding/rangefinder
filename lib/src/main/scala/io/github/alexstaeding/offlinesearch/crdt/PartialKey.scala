package io.github.alexstaeding.offlinesearch.crdt

/**
  * A partial T that represents a cluster of Ts that are "close enough" together.
  */
trait PartialKey[T] {
  val id: T

  def matches(search: T): Boolean
}

class StringPartialKey(
  override val id: String,
) extends PartialKey[String] {
  override def matches(search: String): Boolean = {
    search.startsWith(id)
  }
}
