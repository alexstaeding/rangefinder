package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.util.Try

trait NetworkAdapter[V] {
  def send[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent[V], A]]

  def sendObserverUpdate(update: NodeInfoUpdate): Unit
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec, P: JsonValueCodec](
        bindAddress: InetSocketAddress,
        observerAddress: Option[InetSocketAddress],
        onReceive: EventHandler[V, P],
    )(using logger: Logger): NetworkAdapter[V]
  }
}

trait EventHandler[V, P] {
  def handlePing(request: PingEvent[V]): Either[RedirectEvent[V], PingAnswerEvent[V]]
  def handleFindNode(request: FindNodeEvent[V]): Either[RedirectEvent[V], FindNodeAnswerEvent[V]]
  def handleSearch(request: SearchEvent[V, P]): Either[RedirectEvent[V], SearchAnswerEvent[V, P]]
  def handleStoreValue(request: StoreValueEvent[V, P]): Either[RedirectEvent[V], StoreValueAnswerEvent[V]]
}

extension [V, P](eventHandler: EventHandler[V, P]) {
  def processRequest(request: RequestEvent[V]): Try[RedirectOr[V, AnswerEvent[V]]] =
    Try {
      request match
        case pingEvent: PingEvent[V]             => eventHandler.handlePing(pingEvent)
        case findNodeEvent: FindNodeEvent[V]     => eventHandler.handleFindNode(findNodeEvent)
        case findValueEvent: SearchEvent[V, P]      => eventHandler.handleSearch(findValueEvent)
        case storeValueEvent: StoreValueEvent[V, P] => eventHandler.handleStoreValue(storeValueEvent)
    }
}
