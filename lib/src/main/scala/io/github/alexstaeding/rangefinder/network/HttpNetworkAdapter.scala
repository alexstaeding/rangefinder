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

class HttpNetworkAdapter[V: JsonValueCodec, P: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V, P],
)(using logger: Logger)
    extends NetworkAdapter[V, P] {

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

  override def send[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent, A]] = {
    logger.info(s"Sending message $event to $nextHop")
    val body = writeToString(event)(using RequestEvent.codec)
    val request = HttpHelper.sendJsonPost(nextHop, "/api/v1/message", body)

    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response =>
        logger.info("Received response: " + response)
        // Ambiguous given instances for V and P codec
        readFromString(response.body())(using AnswerEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]]))
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
  def create[V: JsonValueCodec, P: JsonValueCodec](
      bindAddress: InetSocketAddress,
      observerAddress: Option[InetSocketAddress],
      onReceive: EventHandler[V, P],
  )(using logger: Logger): NetworkAdapter[V, P] = HttpNetworkAdapter(bindAddress, observerAddress, onReceive)
}
