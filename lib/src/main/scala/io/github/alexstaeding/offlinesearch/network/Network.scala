package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import scala.concurrent.Future

trait Network {

  def receive(): Future[NetworkEvent]

  def send(nextHop: InetAddress, event: NetworkEvent): Future[Boolean]
}
