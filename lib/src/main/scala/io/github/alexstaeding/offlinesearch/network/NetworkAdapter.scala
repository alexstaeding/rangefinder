package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.net.InetSocketAddress
import scala.concurrent.Future

trait NetworkAdapter[V] {
  def send(nextHop: InetSocketAddress, event: RequestEvent[V]): Future[AnswerEvent[V]]
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec](
        bindAddress: InetSocketAddress,
        onReceive: RequestEvent[V] => AnswerEvent[V],
    ): NetworkAdapter[V]
  }
}
