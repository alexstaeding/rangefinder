package io.github.alexstaeding.offlinesearch.network

import java.net.InetAddress
import scala.concurrent.Future

trait Network[V] {

  def receive(): Future[EventInterceptor[V]]

  def send(nextHop: InetAddress, event: RequestEvent[V]): Future[AnswerEvent[V]]
}
