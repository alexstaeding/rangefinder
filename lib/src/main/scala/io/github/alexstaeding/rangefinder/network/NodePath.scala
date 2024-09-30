package io.github.alexstaeding.rangefinder.network

case class NodePath(node: NodeInfo, prev: Option[NodePath])
