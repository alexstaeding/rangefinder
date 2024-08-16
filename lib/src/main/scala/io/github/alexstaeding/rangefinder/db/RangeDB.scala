package io.github.alexstaeding.rangefinder.db

import io.github.alexstaeding.rangefinder.meta.PartialKey
import io.github.alexstaeding.rangefinder.network.HashingAlgorithm

import scala.concurrent.Future

trait RangeDB[V] {
  def store(value: V)(using HashingAlgorithm[V]): Future[Boolean]
  
  def find(key: PartialKey[V]): V
}
