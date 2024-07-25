package io.github.alexstaeding.offlinesearch.headless

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class ContentBrowser(
    private val bindAddress: InetSocketAddress,
    private val content: Map[String, String]
)(using logger: Logger) {

  private val server = HttpServer.create(bindAddress, 10)

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  {
    server.createContext("/browse/", (exchange: HttpExchange) => {
      val path = exchange.getRequestURI.getPath.split("/").drop(1)
      if (path.length != 2) {
        val error = "Path should contain exactly one element after '/browse/'"
        exchange.sendResponseHeaders(400, error.length)
        exchange.getResponseBody.write(error.getBytes)
      } else {
        val response = content.getOrElse(path(1), "Not found")
        logger.info(s"Getting content for '${path(1)}' -> '$response'")
        exchange.sendResponseHeaders(200, response.length)
        exchange.getResponseBody.write(response.getBytes)
      }
      exchange.close()
    })

    server.setExecutor(ec)
    server.start()
    logger.info(s"Started content browser on $bindAddress")
  }
}
