package io.github.alexstaeding.offlinesearch

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import io.github.alexstaeding.offlinesearch.crdt.{Counter, IndexGroup}
import io.github.alexstaeding.offlinesearch.network.Network

import scala.collection.mutable
import scala.collection.mutable.ArrayDeque
import scala.reflect.Selectable.reflectiveSelectable

class Client[T, P](
    env: {
      val network: Network[T, P]
    },
    val peers: collection.Seq[P],
) {

  var indexGroups = new mutable.ArrayDeque[IndexGroup[T]]

//  def peers = new mutable.ArrayDeque[Peer]

  def discoverPeers(): Unit = {
    writeToString[Counter](Counter(2,3))
    env.network.send(2)
  }
}
