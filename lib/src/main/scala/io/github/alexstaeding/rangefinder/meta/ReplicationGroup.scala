package io.github.alexstaeding.rangefinder.meta

import io.github.alexstaeding.rangefinder.crdt.{NodeScoped, ReplicatedBoolean}

case class ReplicationGroup[T](
    membership: NodeScoped[ReplicatedBoolean],
)

object ReplicationGroup {
  // how many groups are there in total
  val count = 10
}
