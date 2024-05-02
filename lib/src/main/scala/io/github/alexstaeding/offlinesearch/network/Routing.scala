package io.github.alexstaeding.offlinesearch.network

import scala.concurrent.Future

trait Routing[V] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(targetId: NodeId, value: V): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def findValue(targetId: NodeId): Future[V]

  def startReceiver(): Unit

  def putLocal(id: NodeId, value: NodeInfo | V): Boolean
}
