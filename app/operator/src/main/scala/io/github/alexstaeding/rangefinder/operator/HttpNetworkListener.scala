package io.github.alexstaeding.rangefinder.operator

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

private class HttpNetworkListener(
    private val bindAddress: InetSocketAddress,
    private val onReceive: EventReceiver,
)(using logger: Logger) {
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))

  private val server = HttpServer.create(bindAddress, 10)

  {
    server.createContext(
      "/init",
      (exchange: HttpExchange) => {
        val result = onReceive.receiveInit().mkString("\n")
        logger.info(s"Received init request and sending response\n$result")
        exchange.getResponseBody.write(result.getBytes)
        exchange.sendResponseHeaders(200, result.length)
        exchange.close()
      },
    )

    server.createContext(
      "/clean",
      (exchange: HttpExchange) => {
        val result = onReceive.receiveClean()
        logger.info(s"Received clean request and sending response $result")
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
