package io.github.alexstaeding.offlinesearch

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.github.alexstaeding.offlinesearch.network.*

import java.net.InetSocketAddress

class Environment[V](
    private val idSpace: NodeIdSpace,
    private val localNodeId: NodeId,
    private val bindAddress: InetSocketAddress,
)(using codec: JsonValueCodec[V]) {
  implicit val self: Environment[V] = this
  lazy val network: Network[V] = new HttpNetwork[V](bindAddress)
  lazy val routing: Routing[V] = new KademliaRouting[V](idSpace, localNodeId)
}
