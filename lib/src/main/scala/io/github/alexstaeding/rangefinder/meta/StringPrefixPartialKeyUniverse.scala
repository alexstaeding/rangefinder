package io.github.alexstaeding.rangefinder.meta

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
      (start to end).map(_.toString).map(PartialKey.ofString)
    }
  }

  override def splitOnce(key: PartialKey[String]): Seq[PartialKey[String]] = {
    // exclusive key always ends with '\uFFFF'
    val startLen = key.startInclusive.length
    val endLen = key.endExclusive.length - 1
    if (startLen - endLen != 0) {
      throw new IllegalArgumentException(s"$key is not symmetric")
    }

    if (key.startInclusive == key.endExclusive.substring(0, endLen)) {
      ('a' to 'z').map { c =>
        PartialKey.ofString(key.startInclusive + c)
      }
    } else {
      val sharedPrefix = key.startInclusive
        .zip(key.endExclusive)
        .takeWhile { (a, b) => a == b }
        .map { (a, _) => a }
        .mkString

      // for each different suffix char
      (key.startInclusive.charAt(sharedPrefix.length) to key.endExclusive.charAt(sharedPrefix.length))
        .flatMap { bottom =>
          ('a' to 'z').map { c =>
            PartialKey.ofString(sharedPrefix + bottom + c)
          }
        }
    }
  }

  override def split(key: PartialKey[String]): Seq[PartialKey[String]] = ???
}
