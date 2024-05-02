package io.github.alexstaeding.offlinesearch.crdt

import io.github.alexstaeding.offlinesearch.network.NodeId

type NodeScoped[T] = Map[NodeId, T]
