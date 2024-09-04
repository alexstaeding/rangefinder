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
    val delta = Math.abs(startLen - endLen)

    val sharedPrefix = key.startInclusive.zip(key.endExclusive)
      .takeWhile { (a, b) => a == b }
      .map { (a, _) => a }
      .mkString

    if (delta == 0) {
      // simple case
      val bottomRow = if (sharedPrefix.length < startLen) {
        // bottom row is at index after shared prefix
        sharedPrefix.length
      } else {
        // strings are equal, last index is bottom row
        startLen - 1
      }

      (key.startInclusive.charAt(bottomRow) to key.endExclusive.charAt(bottomRow))
        .flatMap { bottom =>
          ('a' to 'z')
            .map { c =>
              // TODO: What if sharedPrefix contains bottom
              PartialKey.ofString(sharedPrefix + bottom + c)
            }
        }



    } else {}
    ???
  }

  override def split(key: PartialKey[String]): Seq[PartialKey[String]] = ???
}
