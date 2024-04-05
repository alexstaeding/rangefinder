package io.github.alexstaeding.offlinesearch.meta

import io.github.alexstaeding.offlinesearch.crdt.{NodeScoped, ReplicatedBoolean}

case class ReplicationGroup[T](
    membership: NodeScoped[ReplicatedBoolean],
)

object ReplicationGroup {
  // how many groups are there in total
  val count = 10
}
