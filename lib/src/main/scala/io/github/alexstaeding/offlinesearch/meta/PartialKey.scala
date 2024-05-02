package io.github.alexstaeding.offlinesearch.meta

import org.apache.logging.log4j.LogManager

/** A partial T that represents a cluster of Ts that are "close enough"
  * together.
  */
case class PartialKey[V](value: V) {
  def foo(): Unit = {
    LogManager.getLogger
  }
}

trait PartialKeyActions[V] {
  extension (partialKey: PartialKey[V]) {
    def matches(search: V): Boolean
  }
}
