package io.github.alexstaeding.rangefinder.meta

/** A partial T represents a range of Ts.
  */
case class PartialKey[+V](startInclusive: V, endExclusive: V) {
  def map[U](f: V => U): PartialKey[U] = PartialKey(f(startInclusive), f(endExclusive))
}

object PartialKey {
  def ofString(value: String): PartialKey[String] = {
    require(!value.isBlank)
    val lowercase = value.toLowerCase
    PartialKey(lowercase, lowercase + '\uFFFF')
  }
}
