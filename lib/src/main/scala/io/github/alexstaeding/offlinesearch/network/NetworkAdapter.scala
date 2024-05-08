package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import scala.concurrent.Future

trait NetworkAdapter[V] {
  def send[C, A <: AnswerEvent[V, C], R <: RequestEvent[V, C, A]](nextHop: InetSocketAddress, event: R): Future[R#Answer]
}

object NetworkAdapter {
  trait Factory {
    def create[V: JsonValueCodec, C, A <: AnswerEvent[V, C], R <: RequestEvent[V, C, A]](
        bindAddress: InetSocketAddress,
        onReceive: R => R#Answer,
    )(using logger: Logger): NetworkAdapter[V]
  }
}
