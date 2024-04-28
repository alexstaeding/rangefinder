package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import scala.concurrent.Future

// TODO: Make generic for T = search type
trait Network {
  
  def receive(): Future[NetworkEvent]

  def ping(nextHop: InetAddress, pingEvent: PingEvent): Future[Boolean]

  def send(to: InetAddress, data: String): Future[Option[String]]
}
