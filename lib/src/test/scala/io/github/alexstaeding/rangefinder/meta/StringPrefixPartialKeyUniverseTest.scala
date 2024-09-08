package io.github.alexstaeding.rangefinder.meta

import org.scalatest.funsuite.AnyFunSuite

class StringPrefixPartialKeyUniverseTest extends AnyFunSuite {

  private val none: Set[PartialKey[String]] = Set.empty

  def partialKeySet(values: String*): Set[PartialKey[String]] = {
    if (values.isEmpty) none else values.toSet.map(PartialKey.ofString)
  }

  test("point value predecessors") {
    val universe = StringPrefixPartialKeyUniverse()

    assert(universe.getIndexKeys("abcdef") == partialKeySet("ab", "abc"))
    assert(universe.getIndexKeys("abcde") == partialKeySet("ab", "abc"))
    assert(universe.getIndexKeys("aba") == partialKeySet("ab", "aba"))
    assert(universe.getIndexKeys("ab") == partialKeySet("ab"))
    assert(universe.getIndexKeys("a") == partialKeySet())
    assert(universe.getIndexKeys("") == partialKeySet())
  }

  test("predecessor only range") {
    val universe = StringPrefixPartialKeyUniverse()

    assert(universe.getIndexKeys(PartialKey.ofString("abcdef")) == partialKeySet("ab", "abc"))
    assert(universe.getIndexKeys(PartialKey.ofString("abcde")) == partialKeySet("ab", "abc"))
    assert(universe.getIndexKeys(PartialKey.ofString("abcd")) == partialKeySet("ab", "abc"))
    assert(universe.getIndexKeys(PartialKey.ofString("abc")) == partialKeySet("ab", "abc")) // <-- failure
    assert(universe.getIndexKeys(PartialKey.ofString("ab")) == partialKeySet("ab"))
    assert(universe.getIndexKeys(PartialKey.ofString("a")) == partialKeySet())
  }
}
