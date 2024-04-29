package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.alexstaeding.offlinesearch.network.event.{NetworkEvent, RequestEvent}

import java.io.{InputStream, OutputStream}
import java.net.http.{HttpClient, HttpRequest}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.util.concurrent.{ConcurrentLinkedDeque, Executors, LinkedBlockingQueue}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, blocking}
import scala.io.Source
import scala.jdk.FutureConverters.CompletionStageOps

class HttpNetwork[V](bindAddress: InetSocketAddress)(using codec: JsonValueCodec[V]) extends Network {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 0)
  private val receiveQueue = new LinkedBlockingQueue[NetworkEvent]()

  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  def createEvent[N <: NetworkEvent](eventFactory: NetworkEvent.SimpleFactory[N], body: InputStream): N = {
    import eventFactory.codec
    readFromString(Source.fromInputStream(body).mkString)
  }

  def createEvent[N[_] <: NetworkEvent](eventFactory: NetworkEvent.ParameterizedFactory[N], body: InputStream): N[V] = {
    import eventFactory.codec
    readFromString(Source.fromInputStream(body).mkString)
  }

  class MessageHandler(val name: String) extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val response = "OK!"
      exchange.sendResponseHeaders(200, response.length)
      exchange.getResponseBody.write(response.getBytes)
      exchange.close()
    }
  }

  {
    for (elem <- RequestEvent.ALL) {
      server.createContext(s"/${elem.name}")
    }
    
    server.start()
  }

  override def receive(): Future[NetworkEvent] = Future { blocking(receiveQueue.poll()) }

  override def send(nextHop: InetAddress, event: NetworkEvent): Future[Boolean] = {
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
}
