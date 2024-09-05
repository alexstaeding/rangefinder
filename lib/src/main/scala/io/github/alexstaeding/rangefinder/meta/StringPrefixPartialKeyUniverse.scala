package io.github.alexstaeding.rangefinder.meta

object StringPrefixPartialKeyUniverse extends PartialKeyUniverse[String] {

  val depth: Int = 2

  override def getRootKey(value: String): PartialKey[String] = {
    if (value.length < depth) {
      throw IllegalArgumentException(
        s"Cannot create a partial key from $value for depth $depth")
    }
    PartialKey.ofString(value.toLowerCase.substring(0, depth))
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
