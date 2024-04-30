package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.event.{AnswerEvent, EventInterceptor, RequestEvent}

import java.net.InetAddress
import scala.concurrent.Future

trait Network[V] {

  def receive(): Future[EventInterceptor[V]]

  def send[R <: RequestEvent: RequestEvent.SimpleFactory](nextHop: InetAddress, event: R): Future[AnswerEvent]

  def send[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](nextHop: InetAddress, event: R[V]): Future[AnswerEvent]
}
