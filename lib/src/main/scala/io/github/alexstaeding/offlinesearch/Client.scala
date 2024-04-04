package io.github.alexstaeding.offlinesearch

import scala.reflect.Selectable.reflectiveSelectable
import scala.collection.mutable.ArrayDeque
import io.github.alexstaeding.offlinesearch.network.Network
import io.github.alexstaeding.offlinesearch.crdt.Counter

class Client[T](env: {
  val network: Network[T]
}) {

  var indexGroups = new ArrayDeque[IndexGroup]

  def peers = new ArrayDeque[Peer]

  def discoverPeers(): Unit = {
    env.network.send(2)
  }
}
