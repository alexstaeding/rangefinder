package io.github.alexstaeding.offlinesearch

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.offlinesearch.network.*

import java.net.InetSocketAddress

class Environment[V](
    private val idSpace: NodeIdSpace,
    private val localNodeInfo: NodeInfo,
)(using codec: JsonValueCodec[V]) {
  implicit val self: Environment[V] = this
  lazy val network: Network[V] = new HttpNetwork[V](localNodeInfo.address)
  lazy val routing: Routing[V] = new KademliaRouting[V](idSpace, localNodeInfo)
}
