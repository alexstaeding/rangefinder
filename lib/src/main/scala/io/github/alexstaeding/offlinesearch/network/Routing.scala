package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.meta.PartialKey

import scala.concurrent.Future

trait Routing[V] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(value: OwnedValue[V]): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def search(key: PartialKey[V]): Future[Seq[OwnedValue[V]]]

  def putLocalNode(id: NodeId, nodeInfo: NodeInfo): Boolean = putLocal(id, Left(nodeInfo))

  def putLocalValue(id: NodeId, value: OwnedValue[V]): Boolean = putLocal(id, Right(value))

  def putLocal(id: NodeId, value: Either[NodeInfo, OwnedValue[V]]): Boolean
}
