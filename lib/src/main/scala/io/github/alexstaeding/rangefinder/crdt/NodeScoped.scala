package io.github.alexstaeding.rangefinder.crdt

import io.github.alexstaeding.rangefinder.network.NodeId

type NodeScoped[T] = Map[NodeId, T]
