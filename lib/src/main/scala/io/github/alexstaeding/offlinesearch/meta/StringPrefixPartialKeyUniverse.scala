package io.github.alexstaeding.offlinesearch.meta

object StringPrefixPartialKeyUniverse extends PartialKeyUniverse[String] {
  override def getRootPartialKey(value: String): PartialKey[String] = {
    if (value.isBlank) {
      throw IllegalArgumentException("Cannot create a partial key for a blank string")
    }
    val firstChar = value.charAt(0).toString
    PartialKey(firstChar, firstChar, Option.empty)
  }
}
