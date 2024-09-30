package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.net.http.{HttpClient, HttpResponse}
import java.util.concurrent.Executors
import scala.concurrent.*
import scala.util.{Failure, Random, Try}

class HttpNetworkAdapter[V: JsonValueCodec, P: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V, P],
)(using logger: Logger)
    extends NetworkAdapter[V, P] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10000)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  server.createContext(
    "/api/v1/message",
    (exchange: HttpExchange) => {
      // Ambiguous given instances for V and P codec
      Try(readFromStream(exchange.getRequestBody)(using RequestEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]])))
        .recoverWith { e =>
          logger.error(s"Failed to parse request", e)
          exchange.sendResponseHeaders(400, 0)
          exchange.close()
          Failure(e)
        }
        .map { request =>
          HttpHelper.receiveRequest(exchange, eventHandler, request)
        }
    },
  )
  server.setExecutor(ec)
  server.start()
  logger.info("Started server on " + bindAddress)

  override def send[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[ErrorEvent, A]] = {
    val body = writeToString(event)(using RequestEvent.codec)
    val request = HttpHelper.buildPost(nextHop, "/api/v1/message", body)

    Future {
      // random wait
      Thread.sleep(Random.nextLong(2000))
    }.flatMap { _ =>
      HttpHelper
        .sendAsync(client, request, nextHop)
        .map { response =>
          // Ambiguous given instances for V and P codec
          readFromString(response.body())(using AnswerEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]]))
        }
        .map { x =>
          x.extractError()
        }
    }
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
