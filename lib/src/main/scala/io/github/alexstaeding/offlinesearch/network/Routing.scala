package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import java.util
import scala.concurrent.Future

trait Routing[V] {

  def ping(id: NodeId): Future[Boolean]

  def store(id: NodeId, value: V): Future[Boolean]

  def findNode(id: NodeId): Future[NodeInfo]

  def findValue(id: NodeId): Future[V]
}
