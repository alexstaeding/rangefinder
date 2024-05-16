package io.github.alexstaeding.offlinesearch.network

trait HashingAlgorithm[V] {
  def hash(value: V): NodeId
}
