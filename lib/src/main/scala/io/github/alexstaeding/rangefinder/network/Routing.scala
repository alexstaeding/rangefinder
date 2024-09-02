package io.github.alexstaeding.rangefinder.network

import io.github.alexstaeding.rangefinder.meta.PartialKey

import scala.concurrent.Future

trait Routing[V, P] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(value: IndexEntry[V, P]): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def search(key: PartialKey[V]): Future[Seq[IndexEntry[V, P]]]

  def putLocalNode(id: NodeId, nodeInfo: NodeInfo): Boolean = putLocal(id, Left(nodeInfo))

  def putLocalValue(id: NodeId, value: IndexEntry[V, P]): Boolean = putLocal(id, Right(value))

  def putLocal(id: NodeId, value: Either[NodeInfo, IndexEntry[V, P]]): Boolean
}
