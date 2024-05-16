package io.github.alexstaeding.offlinesearch.network

import scala.concurrent.Future

trait Routing[V] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(targetId: NodeId, value: V): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def findValue(targetId: NodeId): Future[Option[V]]

  def putLocalNode(id: NodeId, nodeInfo: NodeInfo): Boolean = putLocal(id, Left(nodeInfo))

  def putLocalValue(id: NodeId, value: V): Boolean = putLocal(id, Right(value))

  def putLocal(id: NodeId, value: Either[NodeInfo, V]): Boolean
}
