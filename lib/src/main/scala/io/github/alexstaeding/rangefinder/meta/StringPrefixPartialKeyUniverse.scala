package io.github.alexstaeding.rangefinder.meta

class StringPrefixPartialKeyUniverse(
    val upperBoundInclusive: Int = 2,
    val lowerBoundExclusive: Int = 3,
    val maxSuccessorDelta: Int = 2,
) extends PartialKeyUniverse[String] {

  require(upperBoundInclusive > 0)
  require(maxSuccessorDelta > 0)
  require(lowerBoundExclusive > upperBoundInclusive)

  override def getIndexKeys(value: String): Set[PartialKey[String]] = {
    (upperBoundInclusive to ((lowerBoundExclusive - 1) min value.length)).map { idx =>
      PartialKey.ofString(value.substring(0, idx))
    }.toSet
  }


  override def getIndexKeys(key: PartialKey[String]): Set[PartialKey[String]] = {
    getIndexKeys(key.getSharedPrefix)
  }

  extension (key: PartialKey[String]) {
    private def getSharedPrefix: String =
      key.startInclusive
        .zip(key.endExclusive)
        .takeWhile { (a, b) => a == b }
        .map { (a, _) => a }
        .mkString
  }
}
