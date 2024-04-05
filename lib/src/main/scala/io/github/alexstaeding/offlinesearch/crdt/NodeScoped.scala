package io.github.alexstaeding.offlinesearch.crdt

import io.github.alexstaeding.offlinesearch.NodeId

type NodeScoped[T] = Map[NodeId, T]
