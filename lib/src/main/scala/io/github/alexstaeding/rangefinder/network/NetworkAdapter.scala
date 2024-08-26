package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import scala.concurrent.Future

trait NetworkAdapter[V] {
  def send[R <: RequestEvent[V]](nextHop: InetSocketAddress, event: R): Future[Either[RedirectEvent[V], R#Answer]]

  def sendObserverUpdate(update: NodeInfoUpdate): Unit
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec](
        bindAddress: InetSocketAddress,
        observerAddress: Option[InetSocketAddress],
        onReceive: EventHandler[V],
    )(using logger: Logger): NetworkAdapter[V]
  }
}

trait EventHandler[V] {
  def handlePing(request: PingEvent[V]): Either[RedirectEvent[V], PingAnswerEvent[V]]
  def handleFindNode(request: FindNodeEvent[V]): Either[RedirectEvent[V], FindNodeAnswerEvent[V]]
  def handleSearch(request: SearchEvent[V]): Either[RedirectEvent[V], SearchAnswerEvent[V]]
  def handleStoreValue(request: StoreValueEvent[V]): Either[RedirectEvent[V], StoreValueAnswerEvent[V]]
}
