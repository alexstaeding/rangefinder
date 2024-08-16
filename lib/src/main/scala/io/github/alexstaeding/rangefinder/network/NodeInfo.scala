package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.net.InetSocketAddress

case class NodeInfo(id: NodeId, address: InetSocketAddress)

object NodeInfo {
  given inetAddressCodec: JsonValueCodec[InetSocketAddress] = new JsonValueCodec[InetSocketAddress] {

    override def decodeValue(in: JsonReader, default: InetSocketAddress): InetSocketAddress = {
      val (host, port) = in.readString(null).split(":") match {
        case Array(host)       => throw new IllegalArgumentException(s"Invalid address (missing port): $host")
        case Array(host, port) => (host, port.toInt)
        case _                 => return default
      }
      new InetSocketAddress(host, port)
    }

    override def encodeValue(x: InetSocketAddress, out: JsonWriter): Unit =
      out.writeVal(x.getHostString + ":" + x.getPort)

    override def nullValue: InetSocketAddress = null
  }

  given codec: JsonValueCodec[NodeInfo] = JsonCodecMaker.make
}
