package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import scala.concurrent.Future

trait NetworkAdapter[V] {
  def send[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent[V], A]]
  
  def sendObserverUpdate(update: NodeInfoUpdate): Unit
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec](
        bindAddress: InetSocketAddress,
        observerAddress: Option[InetSocketAddress],
        onReceive: EventReceiver[V],
    )(using logger: Logger): NetworkAdapter[V]
  }
}

trait EventReceiver[V] {
  def receivePing(request: PingEvent[V]): Either[RedirectEvent[V], PingAnswerEvent[V]]
  def receiveFindNode(request: FindNodeEvent[V]): Either[RedirectEvent[V], FindNodeAnswerEvent[V]]
  def receiveSearch(request: SearchEvent[V]): Either[RedirectEvent[V], SearchAnswerEvent[V]]
  def receiveStoreValue(request: StoreValueEvent[V]): Either[RedirectEvent[V], StoreValueAnswerEvent[V]]
}
