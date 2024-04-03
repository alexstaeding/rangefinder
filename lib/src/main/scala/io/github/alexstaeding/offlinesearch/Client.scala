package io.github.alexstaeding.offlinesearch

import scala.reflect.Selectable.reflectiveSelectable
import offlinesearch.network.Network
import scala.collection.mutable.ArrayDeque
import io.github.alexstaeding.crdt.IndexGroup
import offlinesearch.Peer

class Client(env: {
  val network: Network
}) {

  var indexGroups = new ArrayDeque[IndexGroup]

  def peers = new ArrayDeque[Peer]

  def discoverPeers(): Unit = {
    env.network.send(2)
  }
}
