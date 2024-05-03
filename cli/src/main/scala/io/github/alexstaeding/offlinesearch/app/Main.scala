package io.github.alexstaeding.offlinesearch.app

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.network.{NodeId, NodeIdSpace, NodeInfo}
import io.github.alexstaeding.offlinesearch.Client

import java.net.InetSocketAddress
import scala.io.StdIn
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
  val bindAddress = InetSocketAddress("localhost", 9000 + x)
  val localNodeInfo = NodeInfo(localNodeId, bindAddress)
  println(s"localNodeId: '${localNodeId.toHex}' bindAddress: $bindAddress")
  println(s"localInfo: ${localNodeId.toHex},${bindAddress.getHostString},${bindAddress.getPort}")
  val env = Environment[AppDT](idSpace, localNodeInfo)

  val client = Client[AppDT](env)
  env.routing.startReceiver()
  println("Type ping(id) to send a ping to a node")
  while (true) {
    val line = StdIn.readLine()
    line match {
      case s"ping($id)" =>
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            println(s"Sending ping to $nodeId")
            env.routing.ping(nodeId)
          case None =>
            println(s"Invalid node id: '$id'")
      case s"putLocalNode($id,$host,$port)" =>
        NodeId.fromHex(id) match
          case Some(nodeId) =>
            println(s"putLocalNode $nodeId")
            env.routing.putLocal(nodeId, NodeInfo(nodeId, InetSocketAddress(host, port.toInt)))
          case None =>
            println(s"Invalid input, should be nodeId,host,port: '$line'")
      case _ =>
        println("Unknown command")
    }
  }
}
