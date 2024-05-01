package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.alexstaeding.offlinesearch.network.event.NetworkEvent.Meta

import java.io.{InputStream, OutputStream}
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
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
    server.createContext(s"/${summon[NetworkEvent.Meta].name}", new SimpleReceiveHandler[R])

  private def createContext[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](server: HttpServer): Unit =
    server.createContext(s"/${summon[NetworkEvent.Meta].name}", new ParameterizedReceiveHandler[R])
  
  override def receive(): Future[EventInterceptor[V]] = Future { blocking(receiveQueue.poll()) }

  private def send(nextHop: InetAddress, text: String): Future[AnswerEvent[V]] = {
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://${nextHop.getHostAddress}:${bindAddress.getPort}/api/v1/message"))
      .POST(BodyPublishers.ofString(text))
      .build()

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response => readFromString(response.body())(using AnswerEvent.codec) }
      .asScala
  }

  override def send[R <: RequestEvent: RequestEvent.SimpleFactory](nextHop: InetAddress, event: R): Future[AnswerEvent] =
    send(nextHop, writeToString(event)(using summon[RequestEvent.SimpleFactory[R]].codec))

  override def send[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](nextHop: InetAddress, event: R[V]): Future[AnswerEvent] =
    send(nextHop, writeToString(event)(using summon[RequestEvent.ParameterizedFactory[R]].codec))

  private class NetworkEventInterceptor(override val request: RequestEvent) extends EventInterceptor {
    private val promise: Promise[(Int, String)] = Promise()

    def blockingWait(exchange: HttpExchange): Unit = {
      val (responseCode, response) = Await.result(promise.future, 0.nanos)
      exchange.sendResponseHeaders(responseCode, response.length)
      exchange.getResponseBody.write(response.getBytes)
      exchange.close()
    }

    private def checkPromise(): Unit = if (promise.isCompleted) throw IllegalStateException("Already answered")

    override def answer[A <: AnswerEvent: NetworkEvent.Meta](event: A): Unit = {
      checkPromise()
      try promise.success(event.responseCode, writeToString(event)(using summon[NetworkEvent.Meta[A]].codec))
      catch case e: Throwable => promise.failure(e)
    }
  }

  private class SimpleSendHandler[A <: AnswerEvent[V]] extends (HttpResponse[String] => AnswerEvent[V]) {
    override def apply(response: HttpResponse[String]): AnswerEvent[V] = response match
      case x if x.statusCode() == 200 => readFromString(response.body())(using summon[NetworkEvent.Meta[A]].codec)
      case x if x.statusCode() == 301 => readFromString(response.body())(using RedirectEvent.codec)
      case x if x.statusCode() == 404 => readFromString(response.body())(using NotFoundEvent.meta.codec)
      case x                          => throw IllegalStateException(s"Status code: ${x.statusCode()}")
  }

  private class SimpleReceiveHandler[R <: RequestEvent: RequestEvent.SimpleFactory] extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val interceptor = new NetworkEventInterceptor(
        readFromStream(exchange.getRequestBody)(using summon[RequestEvent.SimpleFactory[R]].codec),
      )
      receiveQueue.put(interceptor)
      interceptor.blockingWait(exchange)
    }
  }

  private class ParameterizedReceiveHandler[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory] extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val interceptor = new NetworkEventInterceptor(
        readFromStream(exchange.getRequestBody)(using summon[RequestEvent.ParameterizedFactory[R]].codec),
      )
      receiveQueue.put(interceptor)
      interceptor.blockingWait(exchange)
    }
  }
}
