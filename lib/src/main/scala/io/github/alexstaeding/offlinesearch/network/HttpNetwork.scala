package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.alexstaeding.offlinesearch.network.event.*
import io.github.alexstaeding.offlinesearch.network.event.NetworkEvent.SimpleFactory

import java.io.{InputStream, OutputStream}
import java.net.http.{HttpClient, HttpRequest}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.util.concurrent.{ConcurrentLinkedDeque, Executors, LinkedBlockingQueue}
import scala.concurrent.*
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.jdk.FutureConverters.CompletionStageOps

class HttpNetwork[V](bindAddress: InetSocketAddress)(using codec: JsonValueCodec[V]) extends Network[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 0)
  private val receiveQueue = new LinkedBlockingQueue[EventInterceptor[V]]

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  {
    createContext(server)(using PingEvent)
    createContext(server)(using StoreValueEvent)
    createContext(server)(using FindNodeEvent)
    createContext(server)(using FindValueEvent)
    server.setExecutor(ec)
    server.start()
  }

  private def createContext[R <: RequestEvent: RequestEvent.SimpleFactory](server: HttpServer): Unit =
    server.createContext(s"/${summon[NetworkEvent.Factory].name}", new SimpleEventHandler[R])

  private def createContext[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](server: HttpServer): Unit = {
    server.createContext(s"/${summon[NetworkEvent.Factory].name}", new ParameterizedEventHandler[R])
  }

  override def receive(): Future[EventInterceptor[V]] = Future { blocking(receiveQueue.poll()) }

  override def send(nextHop: InetAddress, event: RequestEvent): Future[Boolean] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://${nextHop.getHostAddress}:${bindAddress.getPort}"))
      .build()

    client
      .sendAsync(request, null)
      .thenApply { response =>
        response.statusCode() == 200
      }
      .asScala
  }

  private class NetworkEventInterceptor(override val request: RequestEvent) extends EventInterceptor[V] {
    private val promise: Promise[(Int, String)] = Promise()

    def blockingWait(exchange: HttpExchange): Unit = {
      val (responseCode, response) = Await.result(promise.future, 0.nanos)
      exchange.sendResponseHeaders(responseCode, response.length)
      exchange.getResponseBody.write(response.getBytes)
      exchange.close()
    }

    private def checkPromise(): Unit = if (promise.isCompleted) throw IllegalStateException("Already answered")

    override def answer[A <: AnswerEvent: AnswerEvent.SimpleFactory](event: A): Unit = {
      checkPromise()
      try promise.success(event.responseCode, writeToString(event)(using summon[AnswerEvent.SimpleFactory[A]].codec))
      catch case e: Throwable => promise.failure(e)
    }

    def answer[A[_] <: AnswerEvent: AnswerEvent.ParameterizedFactory](event: A[V]): Unit = {
      checkPromise()
      try promise.success(event.responseCode, writeToString(event)(using summon[AnswerEvent.ParameterizedFactory[A]].codec))
      catch case e: Throwable => promise.failure(e)
    }
  }

  // TODO: Combine impls
  private class SimpleEventHandler[R <: RequestEvent: RequestEvent.SimpleFactory] extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val interceptor = new NetworkEventInterceptor(
        readFromStream(exchange.getRequestBody)(using summon[RequestEvent.SimpleFactory[R]].codec),
      )
      receiveQueue.put(interceptor)
      interceptor.blockingWait(exchange)
    }
  }

  private class ParameterizedEventHandler[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory] extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val interceptor = new NetworkEventInterceptor(
        readFromStream(exchange.getRequestBody)(using summon[RequestEvent.ParameterizedFactory[R]].codec),
      )
      receiveQueue.put(interceptor)
      interceptor.blockingWait(exchange)
    }
  }
}
