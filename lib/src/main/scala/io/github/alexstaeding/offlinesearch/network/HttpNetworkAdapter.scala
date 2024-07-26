package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.http.HttpClient.Version
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{InetSocketAddress, URI}
import java.util.concurrent.Executors
import scala.concurrent.*
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.Try

class HttpNetworkAdapter[V: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val onReceive: EventReceiver[V],
)(using logger: Logger)
    extends NetworkAdapter[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  {
    server.createContext(
      "/api/v1/message",
      (exchange: HttpExchange) => {
        val request = readFromStream(exchange.getRequestBody)(using RequestEvent.codec)
        val response: String = processRequest(request)
          .recover { case e: Exception =>
            logger.error("Failed to process request", e)
            Right(ErrorEvent(request.requestId, s"Internal server error: ${e.getClass} ${e.getMessage}"))
          }
          .map(serializeAnswer)
          .recover { case e: Exception =>
            logger.error("Failed to serialize response", e)
            """{"type":"ErrorEvent"}"""
          }.get

        logger.info(s"Received message $request and sending response $response")

        exchange.sendResponseHeaders(200, response.length)
        exchange.getResponseBody.write(response.getBytes)
        exchange.close()
      },
    )
    server.setExecutor(ec)
    server.start()
    logger.info("Started server on " + bindAddress)
  }

  private def processRequest(request: RequestEvent[V]): Try[Either[RedirectEvent[V], AnswerEvent[V]]] =
    Try {
      request match
        case pingEvent: PingEvent[V]             => onReceive.receivePing(pingEvent)
        case findNodeEvent: FindNodeEvent[V]     => onReceive.receiveFindNode(findNodeEvent)
        case findValueEvent: SearchEvent[V]      => onReceive.receiveSearch(findValueEvent)
        case storeValueEvent: StoreValueEvent[V] => onReceive.receiveStoreValue(storeValueEvent)
    }

  private def serializeAnswer(answer: Either[RedirectEvent[V], AnswerEvent[V]]): String = {
    logger.info(s"Serializing answer $answer")
    answer match
      case Left(redirect) => writeToString(redirect)(using AnswerEvent.codec)
      case Right(answer)  => writeToString(answer)(using AnswerEvent.codec)
  }

  override def send[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent[V], A]] = {
    logger.info(s"Sending message $event to $nextHop")
    val body = writeToString(event)(using RequestEvent.codec)
    val request = HttpRequest
      .newBuilder()
      .version(Version.HTTP_1_1)
      .uri(URI.create(s"http://${nextHop.getAddress.getHostAddress}:${nextHop.getPort}/api/v1/message"))
      .POST(BodyPublishers.ofString(body))
      .build()

    logger.info(s"Created request: $request with body: $body")
    client
      .sendAsync(request, BodyHandlers.ofString())
      .thenApply { response =>
        logger.info("Received response: " + response)
        readFromString(response.body())(using AnswerEvent.codec)
      }
      .thenApply {
        case redirect: RedirectEvent[V] => Left(redirect)
        case answer =>
          answer match
            case ErrorEvent(_, message) => throw new RuntimeException(message)
            case _                      => Right(answer.asInstanceOf[A])
      }
      .asScala
  }

  override def sendObserverUpdate(update: NodeInfoUpdate): Unit = {
    if (observerAddress.isEmpty) {
      return
    }

    val serializedUpdate = writeToString(update)
    logger.info(s"Sending observer update $serializedUpdate")
    val request = HttpRequest
      .newBuilder()
      .version(Version.HTTP_1_1)
      .uri(URI.create(s"http://${observerAddress.get.getAddress.getHostAddress}:${observerAddress.get.getPort}/visualizer/api/node"))
      .header("Content-Type", "application/json")
      .PUT(BodyPublishers.ofString(serializedUpdate))
      .build()

    logger.info(s"Request: $request")

    try {
      client.send(request, BodyHandlers.ofString()).statusCode() == 200
    } catch {
      case e: Exception =>
        logger.error("Failed to send observer update", e)
    }
  }
}

object HttpNetworkAdapter extends NetworkAdapter.Factory {
  def create[V: JsonValueCodec](
      bindAddress: InetSocketAddress,
      observerAddress: Option[InetSocketAddress],
      onReceive: EventReceiver[V],
  )(using logger: Logger): NetworkAdapter[V] = HttpNetworkAdapter(bindAddress, observerAddress, onReceive)
}
