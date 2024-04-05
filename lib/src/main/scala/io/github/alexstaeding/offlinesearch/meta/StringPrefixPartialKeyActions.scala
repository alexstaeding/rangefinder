package io.github.alexstaeding.offlinesearch.meta

object StringPrefixPartialKeyActions extends PartialKeyActions[String] {
  extension (partialKey: PartialKey[String]) {
    override def matches(search: String): Boolean =
      search.startsWith(partialKey.value)
  }
}
