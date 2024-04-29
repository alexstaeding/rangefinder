package io.github.alexstaeding.offlinesearch

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.github.alexstaeding.offlinesearch.crdt.ReplicatedLong
import io.github.alexstaeding.offlinesearch.meta.ReplicationGroup
import io.github.alexstaeding.offlinesearch.network.Network

import scala.collection.mutable
import scala.collection.mutable.ArrayDeque
import scala.reflect.Selectable.reflectiveSelectable

class Client[T, P](
    env: {
      val network: Network
    },
    val peers: collection.Seq[P],
) {

  var indexGroups = new mutable.ArrayDeque[ReplicationGroup[T]]

//  def peers = new mutable.ArrayDeque[Peer]

  def discoverPeers(): Unit = {
    writeToString[ReplicatedLong](ReplicatedLong(2,3))
//    env.network.send(2)
  }
}
