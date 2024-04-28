package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.NodeId

sealed trait NetworkEvent

case class PingEvent(targetId: NodeId) extends NetworkEvent

case class StoreValueEvent[V](targetId: NodeId) extends NetworkEvent

case class FindNodeEvent(targetId: NodeId) extends NetworkEvent

case class FindValueEvent[V](targetId: NodeId) extends NetworkEvent
