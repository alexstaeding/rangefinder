package io.github.alexstaeding.offlinesearch.network

import java.net.InetSocketAddress
import scala.concurrent.Future

trait Network[V] {

  def receive(): Future[EventInterceptor[V]]

  def send(nextHop: InetSocketAddress, event: RequestEvent[V]): Future[AnswerEvent[V]]
}
