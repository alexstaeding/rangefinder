package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.network.event.{AnswerEvent, RequestEvent}

import java.net.InetAddress
import scala.concurrent.Future

trait Network {

  def receive(): Future[RequestEvent]

  def send(nextHop: InetAddress, event: AnswerEvent): Future[Boolean]
}
