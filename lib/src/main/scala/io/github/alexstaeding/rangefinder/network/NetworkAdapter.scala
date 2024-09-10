package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.util.Try

trait NetworkAdapter[V, P] {
  def send[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[A]

  def sendObserverUpdate(update: NodeInfoUpdate): Unit
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec, P: JsonValueCodec](
        bindAddress: InetSocketAddress,
        observerAddress: Option[InetSocketAddress],
        onReceive: EventHandler[V, P],
    )(using logger: Logger): NetworkAdapter[V, P]
  }
}

trait EventHandler[V, P] {
  def handlePing(request: PingEvent): PingAnswerEvent
  def handleFindNode(request: FindNodeEvent): FindNodeAnswerEvent
  def handleSearch(request: SearchEvent[V, P]): SearchAnswerEvent[V, P]
  def handleStoreValue(request: StoreValueEvent[V, P]): StoreValueAnswerEvent
}

extension [V, P](eventHandler: EventHandler[V, P]) {
  def processRequest(request: RequestEvent[V, P]): Try[AnswerEvent[V, P]] =
    Try {
      request match
        case pingEvent: PingEvent                   => eventHandler.handlePing(pingEvent)
        case findNodeEvent: FindNodeEvent           => eventHandler.handleFindNode(findNodeEvent)
        case findValueEvent: SearchEvent[V, P]      => eventHandler.handleSearch(findValueEvent)
        case storeValueEvent: StoreValueEvent[V, P] => eventHandler.handleStoreValue(storeValueEvent)
    }
}
