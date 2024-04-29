package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.io.{InputStream, OutputStream}
import java.net.http.{HttpClient, HttpRequest}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.util.concurrent.{ConcurrentLinkedDeque, LinkedBlockingQueue}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.io.Source
import scala.jdk.FutureConverters.CompletionStageOps

class HttpNetwork[V](bindAddress: InetSocketAddress)(using codec: JsonValueCodec[V], ec: ExecutionContext) extends Network {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 0)

  def createEvent[N <: NetworkEvent](eventFactory: NetworkEvent.Factory[N], body: InputStream): N = {
    import eventFactory.codec
    readFromString(Source.fromInputStream(body).mkString)
  }

  def createEvent[N[_] <: NetworkEvent](eventFactory: NetworkEvent.ParameterizedFactory[N], body: InputStream): N[V] = {
    import eventFactory.codec
    readFromString(Source.fromInputStream(body).mkString)
  }

  class MessageHandler(val eventFactory: NetworkEvent.Factory[_] | NetworkEvent.ParameterizedFactory[_]) extends HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val response = "OK!"
      exchange.sendResponseHeaders(200, response.length)
      exchange.getResponseBody.write(response.getBytes)
      exchange.close()
    }
  }

  {
    server.start()
  }

  private val queue = new LinkedBlockingQueue[NetworkEvent]()

  override def receive(): Future[NetworkEvent] = Future { blocking(queue.poll()) }

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
