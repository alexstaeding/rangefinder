package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

case class NodeInfoUpdate(
    id: String,
    peers: Seq[PeerUpdate],
    contentUrl: Option[String],
    contentKeys: Option[Seq[String]],
)

object NodeInfoUpdate {
  given codec: JsonValueCodec[NodeInfoUpdate] = JsonCodecMaker.make
}

case class PeerUpdate(id: String, nodeType: String)

object PeerUpdate {
  given codec: JsonValueCodec[PeerUpdate] = JsonCodecMaker.make

}
