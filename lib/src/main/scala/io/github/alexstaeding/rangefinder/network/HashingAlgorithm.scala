package io.github.alexstaeding.rangefinder.network

import io.github.alexstaeding.rangefinder.meta.PartialKey

trait HashingAlgorithm[V] {
  def hash(value: PartialKey[V]): NodeId
}
