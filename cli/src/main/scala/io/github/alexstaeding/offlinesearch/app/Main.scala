package io.github.alexstaeding.offlinesearch.app

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.{NodeId, NodeIdSpace}
import io.github.alexstaeding.offlinesearch.{Client, Environment}

import java.net.InetSocketAddress
import scala.util.Random

implicit val idSpace: NodeIdSpace = NodeIdSpace(16)

case class AppDT(data: String)

object AppDT {
  given codec: JsonValueCodec[AppDT] = JsonCodecMaker.make
}

@main
def hello(): Unit = {
  val x = Random.nextInt(10)
  println(s"Starting client $x")
  val localNodeId = NodeId.generateRandom
  val bindAddress = new InetSocketAddress("localhost", 9000 + x)
  println(s"localNodeId: '$localNodeId' bindAddress: $bindAddress")
  val env = Environment[AppDT](idSpace, localNodeId, bindAddress)

  val client = Client[AppDT](env)
}
