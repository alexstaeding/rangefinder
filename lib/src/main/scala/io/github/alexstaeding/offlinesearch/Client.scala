package io.github.alexstaeding.offlinesearch

import io.github.alexstaeding.offlinesearch.meta.ReplicationGroup

import scala.collection.mutable
import scala.collection.mutable.ArrayDeque

class Client[V] {

  var indexGroups = new mutable.ArrayDeque[ReplicationGroup[V]]

//  def peers = new mutable.ArrayDeque[Peer]
}
