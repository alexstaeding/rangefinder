package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.event.{AnswerEvent, EventInterceptor, RequestEvent}

import java.net.InetAddress
import scala.concurrent.Future

trait Network[V] {

  def receive(): Future[EventInterceptor[V]]
  
  def send(nextHop: InetAddress, event: RequestEvent): Future[AnswerEvent]
}
