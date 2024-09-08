package io.github.alexstaeding.rangefinder.meta

import scala.collection.mutable

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

  // each level required to reach depth multiplies the number of root keys by that level's degree
  //      // e.g. input = fo, depth = 4, delta = 2
  //      // output = foaa, foab, ..., foza, ... fozz
  //      def generateCombinations(prefix: Seq[Char], depth: Int): Set[PartialKey[String]] = {
  //        if (depth == 0) {
  //          Set(PartialKey.ofString(prefix.mkString))
  //        } else {
  //          ('a' to 'z').flatMap { bottom =>
  //            generateCombinations(prefix :+ bottom, depth - 1)
  //          }.toSet
  //        }
  //      }
  //
  //      (lowerBoundExclusive until upperBoundInclusive).flatMap { idx =>
  //        if (idx - value.length <= maxSuccessorLevel) {
  //          generateCombinations(value, idx)
  //        } else {
  //          Set.empty
  //        }
  //      }.toSet

  override def getIndexKeys(key: PartialKey[String]): Set[PartialKey[String]] = {
    // assume key.startInclusive.length == key.endExclusive.length - 1
    require(key.startInclusive.length == key.endExclusive.length - 1)

    val sharedPrefix = key.getSharedPrefix

    if (sharedPrefix.length + maxSuccessorDelta < upperBoundInclusive) {
      return Set.empty
    }

    val restStart = key.startInclusive.substring(sharedPrefix.length)
    val restEnd = key.endExclusive.substring(sharedPrefix.length)

    val queue = new mutable.ArrayDeque[String]

    (sharedPrefix.length to ((lowerBoundExclusive - 1) min key.startInclusive.length))
      .take(maxSuccessorDelta)
      .foreach { idx =>
        (key.startInclusive.charAt(idx) to key.endExclusive.charAt(idx)).map { char =>


        }
      }

    ???
  }

  def predecessors(key: PartialKey[String]): Set[PartialKey[String]] = {
    // assume key.startInclusive.length == key.endExclusive.length - 1

    val sharedPrefix = key.getSharedPrefix

//    val normalized = PartialKey(key.startInclusive.slice(0, lowerBoundExclusive), key.endExclusive)
    ???
  }

  def successors(key: PartialKey[String], levelsLeft: Int): Set[PartialKey[String]] = {
    if (levelsLeft <= 0) {
      return Set.empty
    }

    if (key.startInclusive == key.endExclusive.dropRight(1)) {} else {}

    val sharedPrefix = key.getSharedPrefix

    (key.startInclusive.charAt(sharedPrefix.length) to key.endExclusive.charAt(sharedPrefix.length)).flatMap { bottom =>
      successors(PartialKey.ofString(sharedPrefix + bottom), levelsLeft - 1)
    }.toSet
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
