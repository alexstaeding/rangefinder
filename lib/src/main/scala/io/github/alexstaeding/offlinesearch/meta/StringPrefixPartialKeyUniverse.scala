package io.github.alexstaeding.offlinesearch.meta

object StringPrefixPartialKeyUniverse extends PartialKeyUniverse[String] {
  override def getRootKey(value: String): PartialKey[String] = {
    if (value.isBlank) {
      throw IllegalArgumentException("Cannot create a partial key for a blank string")
    }
    val firstChar = value.charAt(0).toString
    PartialKey(firstChar, firstChar)
  }

  override def getOverlappingRootKeys(key: PartialKey[String]): Seq[PartialKey[String]] = {
    if (key.startInclusive == key.endExclusive) {
      Seq(getRootKey(key.startInclusive))
    } else {
      val start = key.startInclusive.charAt(0)
      val end = key.endExclusive.charAt(0)
      (start to end).map(_.toString).map(PartialKey.ofOne)
    }
  }
}
