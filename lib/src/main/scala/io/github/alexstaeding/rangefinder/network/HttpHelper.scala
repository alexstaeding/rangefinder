package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToString}
import com.sun.net.httpserver.HttpExchange
import org.apache.logging.log4j.Logger

import java.net.http.HttpClient.Version
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{InetSocketAddress, URI}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters.CompletionStageOps

object HttpHelper {

  def sendAsync(
      client: HttpClient,
      request: HttpRequest,
      nextHop: InetSocketAddress,
  )(using logger: Logger, ec: ExecutionContext): Future[HttpResponse[String]] = {
    client
      .sendAsync(request, BodyHandlers.ofString())
      .asScala
      .recoverWith { e =>
        logger.error(s"Failed to receive response from $nextHop", e)
        Future.failed(e)
      }
  }
  def buildPost(address: InetSocketAddress, path: String, body: String): HttpRequest = {
    HttpRequest
      .newBuilder()
      .version(Version.HTTP_1_1)
      .uri(URI.create(s"http://${address.getAddress.getHostAddress}:${address.getPort}$path"))
      .header("Content-Type", "application/json")
      .POST(BodyPublishers.ofString(body))
      .build()
  }

  def sendObserverUpdate(client: HttpClient, observerAddress: InetSocketAddress, update: NodeInfoUpdate)(using logger: Logger): Unit = {
    val serializedUpdate = writeToString(update)
    val request = HttpHelper.buildPost(observerAddress, "/visualizer/api/node", serializedUpdate)

    try {
      client.send(request, BodyHandlers.ofString())
    } catch {
      case e: Exception =>
        logger.error("Failed to send observer update", e)
    }
  }

  def receiveRequest[V: JsonValueCodec, P: JsonValueCodec](
      exchange: HttpExchange,
      eventHandler: EventHandler[V, P],
      request: RequestEvent[V, P],
  )(using logger: Logger): Unit = {
    val response: String = eventHandler
      .processRequest(request)
      .recover { case e: Exception =>
        logger.error(s"Failed to process request $request", e)
        Right(request.createError(s"Internal server error: ${e.getClass} ${e.getMessage}"))
      }
      .map(serializeAnswer)
      .recover { case e: Exception =>
        logger.error("Failed to serialize response", e)
        """{"type":"ErrorEvent"}"""
      }
      .get

    exchange.sendResponseHeaders(200, response.length)
    exchange.getResponseBody.write(response.getBytes)
    exchange.close()
  }

  private def serializeAnswer[V: JsonValueCodec, P: JsonValueCodec](
      answer: Either[ErrorEvent, AnswerEvent[V, P]],
  )(using logger: Logger): String = {
    answer match
      case Left(error) =>
        // Ambiguous given instances for V and P codec
        logger.warn(s"Sending ErrorEvent ${error.content}")
        writeToString(error)(using AnswerEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]]))
      case Right(answer) =>
        writeToString(answer)(using AnswerEvent.codec)
  }
}
