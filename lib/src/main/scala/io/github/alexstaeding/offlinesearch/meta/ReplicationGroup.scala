package io.github.alexstaeding.offlinesearch.meta

import io.github.alexstaeding.offlinesearch.NodeId
import io.github.alexstaeding.offlinesearch.crdt.ReplicatedBoolean

case class ReplicationGroup[T](
    membership: Map[NodeId, ReplicatedBoolean],
)

object ReplicationGroup {
  // how many groups are there in total
  val count = 10
}
