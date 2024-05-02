package io.github.alexstaeding.offlinesearch

import io.github.alexstaeding.offlinesearch.meta.ReplicationGroup
import io.github.alexstaeding.offlinesearch.network.Network

import scala.collection.mutable
import scala.collection.mutable.ArrayDeque

class Client[V](
    env: {
      val network: Network[V]
    },
//    val peers: collection.Seq[P],
) {

  var indexGroups = new mutable.ArrayDeque[ReplicationGroup[V]]

//  def peers = new mutable.ArrayDeque[Peer]
}
