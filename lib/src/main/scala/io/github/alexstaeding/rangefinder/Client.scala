package io.github.alexstaeding.rangefinder

import io.github.alexstaeding.rangefinder.meta.ReplicationGroup

import scala.collection.mutable
import scala.collection.mutable.ArrayDeque

class Client[V] {

  var indexGroups = new mutable.ArrayDeque[ReplicationGroup[V]]

//  def peers = new mutable.ArrayDeque[Peer]
}
