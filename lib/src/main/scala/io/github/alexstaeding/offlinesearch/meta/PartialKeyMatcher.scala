package io.github.alexstaeding.offlinesearch.meta

trait PartialKeyMatcher[V] {
  extension (partialKey: PartialKey[V]) {
    def matches(search: V): Boolean
  }
}
