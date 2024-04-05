package io.github.alexstaeding.offlinesearch

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.net.InetAddress

case class NodeId(host: String)

object NodeId {
  given codec: JsonKeyCodec[NodeId] = new JsonKeyCodec[NodeId] {
    override def decodeKey(in: JsonReader): NodeId = new NodeId(in.readKeyAsString())
    override def encodeKey(x: NodeId, out: JsonWriter): Unit = out.writeKey(x.host)
  }

  given Conversion[NodeId, InetAddress] = id => InetAddress.getByName(id.host)
}
