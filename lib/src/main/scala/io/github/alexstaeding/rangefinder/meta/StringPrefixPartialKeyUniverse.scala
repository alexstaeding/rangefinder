package io.github.alexstaeding.rangefinder.meta

object StringPrefixPartialKeyUniverse extends PartialKeyUniverse[String] {

  val depth: Int = 2

  override def getRootKey(value: String): PartialKey[String] = {
    if (value.length < depth) {
      throw IllegalArgumentException(s"Cannot create a partial key from $value for depth $depth")
    }
    PartialKey.ofString(value.substring(0, depth))
  }

  override def getOverlappingRootKeys(key: PartialKey[String]): Seq[PartialKey[String]] = {
    if (key.startInclusive == key.endExclusive.dropRight(1)) {
      Seq(getRootKey(key.startInclusive))
    } else {
      val sharedPrefix = key.startInclusive
        .zip(key.endExclusive)
        .takeWhile { (a, b) => a == b }
        .map { (a, _) => a }
        .mkString

      (key.startInclusive.charAt(sharedPrefix.length) to key.endExclusive.charAt(sharedPrefix.length))
        .map { bottom =>
          PartialKey.ofString(sharedPrefix + bottom)
        }
    }
  }
}
