package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.util.concurrent.{Executors, LinkedBlockingQueue}
import scala.concurrent.*
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.CompletionStageOps

class HttpNetwork[V](bindAddress: InetSocketAddress)(using codec: JsonValueCodec[V]) extends Network[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 0)
  private val receiveQueue = new LinkedBlockingQueue[EventInterceptor[V]]

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  {
    server.createContext(
      "/api/v1/message",
      (exchange: HttpExchange) => {
        val interceptor = new NetworkEventInterceptor(readFromStream(exchange.getRequestBody)(using RequestEvent.codec))
        receiveQueue.put(interceptor)
        val response = interceptor.awaitResponse()
        exchange.sendResponseHeaders(200, response.length)
        exchange.getResponseBody.write(response.getBytes)
        exchange.close()
      },
    )
    server.setExecutor(ec)
    server.start()
  }

  override def receive(): Future[EventInterceptor[V]] = Future { blocking(receiveQueue.poll()) }

  override def send(nextHop: InetAddress, event: RequestEvent[V]): Future[AnswerEvent[V]] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://${nextHop.getHostAddress}:${bindAddress.getPort}/api/v1/message"))
      .POST(BodyPublishers.ofString(writeToString(event)))
      .build()

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response => readFromString(response.body())(using AnswerEvent.codec) }
      .asScala
  }

  private class NetworkEventInterceptor(override val request: RequestEvent[V]) extends EventInterceptor[V] {
    private val promise: Promise[String] = Promise()
    def awaitResponse(): String = Await.result(promise.future, 0.nanos)

    private def checkPromise(): Unit = if (promise.isCompleted) throw IllegalStateException("Already answered")

    override def answer[A <: AnswerEvent[V]](event: A): Unit = {
      checkPromise()
      try promise.success(writeToString(event)(using AnswerEvent.codec))
      catch case e: Throwable => promise.failure(e)
    }
  }
}
