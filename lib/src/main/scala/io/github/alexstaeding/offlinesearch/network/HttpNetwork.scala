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
import scala.util.{Failure, Success}

class HttpNetwork[V](bindAddress: InetSocketAddress)(using codec: JsonValueCodec[V]) extends Network[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  private val receiveQueue = new LinkedBlockingQueue[EventInterceptor[V]]

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  {
    server.createContext(
      "/api/v1/message",
      (exchange: HttpExchange) => {
        val interceptor = new NetworkEventInterceptor(readFromStream(exchange.getRequestBody)(using RequestEvent.codec))
        receiveQueue.put(interceptor)
        interceptor.future.onComplete {
          case Success(response) =>
            exchange.sendResponseHeaders(200, 0)
            exchange.getResponseBody.write(response.getBytes)
            exchange.close()
          case Failure(exception) =>
            exchange.sendResponseHeaders(500, 0)
            exchange.close()
            exception.printStackTrace()
            throw exception
        }
      },
    )
    server.setExecutor(ec)
    server.start()
    println("Started server on " + bindAddress)
  }

  override def receive(): Future[EventInterceptor[V]] = Future { blocking(receiveQueue.poll()) }

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

  private class NetworkEventInterceptor(override val request: RequestEvent[V]) extends EventInterceptor[V] {
    private val promise: Promise[String] = Promise()
    val future: Future[String] = promise.future
//    def awaitResponse(): String = Await.result(promise.future, 2.seconds)

    private def checkPromise(): Unit = if (promise.isCompleted) throw IllegalStateException("Already answered")

    override def answer[A <: AnswerEvent[V]](event: A): Unit = {
      checkPromise()
      try promise.success(writeToString(event)(using AnswerEvent.codec))
      catch case e: Throwable => promise.failure(e)
    }
  }
}
