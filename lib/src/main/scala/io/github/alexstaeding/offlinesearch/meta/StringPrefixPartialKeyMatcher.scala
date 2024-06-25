package io.github.alexstaeding.offlinesearch.meta


object StringPrefixPartialKeyMatcher extends PartialKeyMatcher[String] {
  extension (partialKey: PartialKey[String]) {
    override def matches(search: String): Boolean =
      partialKey.startInclusive <= search && search < partialKey.endExclusive + "\uFFFF"
  }
}
