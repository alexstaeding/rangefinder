package io.github.alexstaeding.offlinesearch.operator

import com.github.plokhotnyuk.jsoniter_scala.core.readFromStream
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.github.alexstaeding.offlinesearch.network.{NodeId, NodeIdSpace}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

private class HttpNetworkListener(
    private val bindAddress: InetSocketAddress,
    private val onReceive: EventReceiver,
)(using logger: Logger) {
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val server = HttpServer.create(bindAddress, 10)

  {
    server.createContext(
      "/api/v1/add-node",
      (exchange: HttpExchange) => {
        val result = onReceive.receiveAddNode()
        logger.info(s"Received add-node request and sending response $result")
        exchange.sendResponseHeaders(if (result) 200 else 500, 0)
        exchange.close()
      },
    )

    server.createContext(
      "/api/v1/remove-node",
      (exchange: HttpExchange) => {
        val id = readFromStream(exchange.getRequestBody)(using NodeId.valueCodec)
        val result = onReceive.receiveRemoveNode(id)
        logger.info(s"Received remove-node request for $id and sending response $result")
        exchange.sendResponseHeaders(if (result) 200 else 500, 0)
        exchange.close()
      },
    )

    server.setExecutor(ec)
    server.start()
    logger.info("Started server on " + bindAddress)
  }
}

object HttpNetworkListener extends NetworkListener {
  override def register(
      bindAddress: InetSocketAddress,
      onReceive: EventReceiver,
  )(using logger: Logger): Unit = HttpNetworkListener(bindAddress, onReceive)
}
