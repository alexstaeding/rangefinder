package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{InetSocketAddress, URI}
import java.util.concurrent.{Executors, LinkedBlockingQueue}
import scala.concurrent.*
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

class HttpNetworkAdapter[V](
    private val bindAddress: InetSocketAddress,
    private val onReceive: RequestEvent[V] => AnswerEvent[V],
)(using codec: JsonValueCodec[V])
    extends NetworkAdapter[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  {
    server.createContext(
      "/api/v1/message",
      (exchange: HttpExchange) => {
        println("Received message")
        val answer = onReceive(readFromStream(exchange.getRequestBody)(using RequestEvent.codec))
        val response = writeToString(answer)
        exchange.sendResponseHeaders(200, response.length)
        exchange.getResponseBody.write(response.getBytes)
        exchange.close()
      },
    )
    server.setExecutor(ec)
    server.start()
    println("Started server on " + bindAddress)
  }

  override def send(nextHop: InetSocketAddress, event: RequestEvent[V]): Future[AnswerEvent[V]] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://${nextHop.getAddress.getHostAddress}:${nextHop.getPort}/api/v1/message"))
      .POST(BodyPublishers.ofString(writeToString(event)))
      .build()

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response => readFromString(response.body())(using AnswerEvent.codec) }
      .asScala
  }
}

object HttpNetworkAdapter extends NetworkAdapter.Factory {
  override def create[V: JsonValueCodec](
      bindAddress: InetSocketAddress,
      onReceive: RequestEvent[V] => AnswerEvent[V],
  ): NetworkAdapter[V] = HttpNetworkAdapter(bindAddress, onReceive)
}
