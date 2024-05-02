package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import java.net.InetAddress

case class NodeInfo(id: NodeId, ip: InetAddress)

object NodeInfo {
  given inetAddressCodec: JsonValueCodec[InetAddress] = new JsonValueCodec[InetAddress] {

    override def decodeValue(in: JsonReader, default: InetAddress): InetAddress = {
      val hostAddress = in.readString(null)
      if (hostAddress == null) default else InetAddress.getByName(hostAddress)
    }

    override def encodeValue(x: InetAddress, out: JsonWriter): Unit =
      out.writeVal(if (x == null) null else x.getHostAddress)

    override def nullValue: InetAddress = null
  }

  given codec: JsonValueCodec[NodeInfo] = JsonCodecMaker.make
}
