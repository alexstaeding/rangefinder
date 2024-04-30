package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.alexstaeding.offlinesearch.network.event.*
import io.github.alexstaeding.offlinesearch.network.event.NetworkEvent.SimpleFactory

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
  private val sendHandlers: Map[String, HttpResponse[String] => AnswerEvent] = Map(
    createSendHandler(using PingAnswerEvent),
    createSendHandler(using StoreValueAnswerEvent),
    createSendHandler(using FindNodeAnswerEvent),
    createSendHandler(using FindValueAnswerEvent),
  )

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
    server.createContext(s"/${summon[NetworkEvent.Factory].name}", new SimpleReceiveHandler[R])

  private def createContext[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](server: HttpServer): Unit =
    server.createContext(s"/${summon[NetworkEvent.Factory].name}", new ParameterizedReceiveHandler[R])

  private def createSendHandler[A <: AnswerEvent: AnswerEvent.SimpleFactory]: (String, SimpleSendHandler[A]) =
    (summon[NetworkEvent.Factory].name, new SimpleSendHandler)

  private def createSendHandler[A[_] <: AnswerEvent: AnswerEvent.ParameterizedFactory]: (String, ParameterizedSendHandler[A]) =
    (summon[NetworkEvent.Factory].name, new ParameterizedSendHandler)

  override def receive(): Future[EventInterceptor[V]] = Future { blocking(receiveQueue.poll()) }

  private def send(nextHop: InetAddress, text: String)(using factory: RequestEvent.Factory): Future[AnswerEvent] = {
    val handler = sendHandlers(factory.answerName)
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://${nextHop.getHostAddress}:${bindAddress.getPort}/${factory.name}"))
      .POST(BodyPublishers.ofString(text))
      .build()

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { handler(_) }
      .asScala
  }

  override def send[R <: RequestEvent: RequestEvent.SimpleFactory](nextHop: InetAddress, event: R): Future[AnswerEvent] =
    send(nextHop, writeToString(event)(using summon[RequestEvent.SimpleFactory[R]].codec))

  override def send[R[_] <: RequestEvent: RequestEvent.ParameterizedFactory](nextHop: InetAddress, event: R[V]): Future[AnswerEvent] =
    send(nextHop, writeToString(event)(using summon[RequestEvent.ParameterizedFactory[R]].codec))

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
  private class SimpleSendHandler[A <: AnswerEvent: AnswerEvent.SimpleFactory] extends (HttpResponse[String] => AnswerEvent) {
    override def apply(response: HttpResponse[String]): AnswerEvent = response match
      case x if x.statusCode() == 200 => readFromString(response.body())(using summon[AnswerEvent.SimpleFactory[A]].codec)
      case x if x.statusCode() == 301 => readFromString(response.body())(using RedirectEvent.codec)
      case x if x.statusCode() == 404 => readFromString(response.body())(using NotFoundEvent.codec)
      case x                          => throw IllegalStateException(s"Status code: ${x.statusCode()}")
  }

  private class ParameterizedSendHandler[A[_] <: AnswerEvent: AnswerEvent.ParameterizedFactory]
      extends (HttpResponse[String] => AnswerEvent) {
    override def apply(response: HttpResponse[String]): AnswerEvent = response match
      case x if x.statusCode() == 200 => readFromString(response.body())(using summon[AnswerEvent.ParameterizedFactory[A]].codec)
      case x if x.statusCode() == 301 => readFromString(response.body())(using RedirectEvent.codec)
      case x if x.statusCode() == 404 => readFromString(response.body())(using NotFoundEvent.codec)
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
