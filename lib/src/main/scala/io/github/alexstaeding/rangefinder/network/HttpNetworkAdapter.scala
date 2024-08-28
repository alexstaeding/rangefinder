package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.Executors
import scala.concurrent.*
import scala.jdk.FutureConverters.CompletionStageOps

class HttpNetworkAdapter[V: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V],
)(using logger: Logger)
    extends NetworkAdapter[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  server.createContext(
    "/api/v1/message",
    (exchange: HttpExchange) => {
      HttpHelper.receiveRequest(exchange, eventHandler, readFromStream(exchange.getRequestBody)(using RequestEvent.codec))
    },
  )
  server.setExecutor(ec)
  server.start()
  logger.info("Started server on " + bindAddress)

  override def send[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent[V], A]] = {
    logger.info(s"Sending message $event to $nextHop")
    val body = writeToString(event)(using RequestEvent.codec)
    val request = HttpHelper.sendJsonPost(nextHop, "/api/v1/message", body)

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response =>
        logger.info("Received response: " + response)
        readFromString(response.body())(using AnswerEvent.codec)
      }
      .thenApply { x =>
        x.asInstanceOf[A].extractRedirect()
      }
      .asScala
  }

  override def sendObserverUpdate(update: NodeInfoUpdate): Unit =
    observerAddress.foreach(HttpHelper.sendObserverUpdate(client, _, update))
}

object HttpNetworkAdapter extends NetworkAdapter.Factory {
  def create[V: JsonValueCodec](
      bindAddress: InetSocketAddress,
      observerAddress: Option[InetSocketAddress],
      onReceive: EventHandler[V],
  )(using logger: Logger): NetworkAdapter[V] = HttpNetworkAdapter(bindAddress, observerAddress, onReceive)
}
