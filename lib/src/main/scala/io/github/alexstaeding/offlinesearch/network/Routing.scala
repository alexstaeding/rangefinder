package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import java.util
import scala.concurrent.Future

trait Routing[V] {

  def ping(targetId: NodeId): Future[Boolean]

  def store(targetId: NodeId, value: V): Future[Boolean]

  def findNode(targetId: NodeId): Future[NodeInfo]

  def findValue(targetId: NodeId): Future[V]
}
