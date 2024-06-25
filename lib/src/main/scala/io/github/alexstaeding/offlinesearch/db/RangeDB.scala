package io.github.alexstaeding.offlinesearch.db

import io.github.alexstaeding.offlinesearch.meta.PartialKey
import io.github.alexstaeding.offlinesearch.network.HashingAlgorithm

import scala.concurrent.Future

trait RangeDB[V] {
  def store(value: V)(using HashingAlgorithm[V]): Future[Boolean]
  
  def find(key: PartialKey[V]): V
}
