package io.github.alexstaeding.rangefinder.meta

object StringPrefixPartialKeyUniverse extends PartialKeyUniverse[String] {

  private val depth = 2

  override def getRootKey(value: String): PartialKey[String] = {
    if (value.isBlank) {
      throw IllegalArgumentException("Cannot create a partial key for a blank string")
    }
    val prefix = value.substring(0, depth)
    PartialKey(prefix, prefix)
  }

  override def getOverlappingRootKeys(key: PartialKey[String]): Seq[PartialKey[String]] = {
    if (key.startInclusive == key.endExclusive) {
      Seq(getRootKey(key.startInclusive))
    } else {
      val start = key.startInclusive.charAt(0)
      val end = key.endExclusive.charAt(0)
      (start to end).map(_.toString).map(PartialKey.ofString)
    }
  }
}
