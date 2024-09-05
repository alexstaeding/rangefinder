package io.github.alexstaeding.rangefinder.meta

class StringPrefixPartialKeyUniverse(
    val upperBoundInclusive: Int = 2,
    val lowerBoundExclusive: Int = 3,
    val maxDelta: Int = 2,
) extends PartialKeyUniverse[String] {
  override def getIndexKeys(value: String): Seq[PartialKey[String]] = {
    if (value.length >= lowerBoundExclusive) {
      (lowerBoundExclusive until upperBoundInclusive).map { idx =>
        PartialKey.ofString(value.substring(0, idx))
      }
    } else {
      // each level required to reach depth multiplies the number of root keys by that level's degree
      // e.g. input = fo, depth = 4, delta = 2
      // output = foaa, foab, ..., foza, ... fozz
      def generateCombinations(prefix: Seq[Char], depth: Int): Seq[PartialKey[String]] = {
        if (depth == 0) {
          Seq(PartialKey.ofString(prefix.mkString))
        } else {
          ('a' to 'z').flatMap { bottom =>
            generateCombinations(prefix :+ bottom, depth - 1)
          }
        }
      }

      (lowerBoundExclusive until upperBoundInclusive).flatMap { idx =>
        if (idx - value.length <= maxDelta) {
          generateCombinations(value, idx)
        } else {
          Seq.empty
        }
      }
    }
  }

  override def getIndexKeys(key: PartialKey[String]): Seq[PartialKey[String]] = {
    if (key.startInclusive == key.endExclusive.dropRight(1)) {
      getIndexKeys(key.startInclusive)
    } else {
      val sharedPrefix = key.startInclusive
        .zip(key.endExclusive)
        .takeWhile { (a, b) => a == b }
        .map { (a, _) => a }
        .mkString

      (key.startInclusive.charAt(sharedPrefix.length) to key.endExclusive.charAt(sharedPrefix.length))
        .flatMap { bottom =>
          getIndexKeys(sharedPrefix + bottom)
        }
    }
  }
}
