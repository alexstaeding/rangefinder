package io.github.alexstaeding.rangefinder.network

import io.github.alexstaeding.rangefinder.meta.PartialKey

import scala.concurrent.Future

trait Routing[V, P] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(entry: IndexEntry[V, P]): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def search(key: PartialKey[V]): Future[Set[IndexEntry[V, P]]]

  def searchWithPath(key: PartialKey[V]): Future[Set[(IndexEntry[V, P], NodePath)]]

  def putLocalNode(id: NodeId, nodeInfo: NodeInfo): Boolean

  def putLocalValue(id: NodeId, entry: IndexEntry[V, P]): Boolean
}
