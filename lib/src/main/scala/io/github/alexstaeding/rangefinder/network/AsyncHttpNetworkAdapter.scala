package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromStream, writeToString}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.logging.log4j.Logger

import java.net.InetSocketAddress
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.Try

class AsyncHttpNetworkAdapter[V: JsonValueCodec, P: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V, P],
    private val pipelines: BroadcastPipelines[V, P],
)(using logger: Logger)
    extends NetworkAdapter[V, P] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 1000)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))

  private val answerCache: mutable.Map[UUID, AnswerFuture[?]] = new mutable.HashMap

  private def receiveAnswer[A <: AnswerEvent[V, P]](answer: A): Unit = {
    answerCache.get(answer.requestId) match
      case Some(future) => future.asInstanceOf[AnswerFuture[A]].receive(answer.extractError())
      case None         => logger.error(s"Received answer for unknown request ${answer.requestId}")
  }

  server.createContext(
    "/api/v1/async-request",
    (exchange: HttpExchange) => {
      HttpHelper.receiveRequest(exchange, eventHandler, readFromStream(exchange.getRequestBody)(using RequestEvent.codec))
    },
  )

  server.createContext(
    "/api/v1/async-answer",
    (exchange: HttpExchange) => {
      // Ambiguous given instances for V and P codec
      val answer =
        readFromStream(exchange.getRequestBody)(using AnswerEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]]))
      logger.info(s"Received answer $answer to endpoint /api/v1/async-answer")
      receiveAnswer(answer)
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    },
  )

  server.setExecutor(ec)
  server.start()

  override def send[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[ErrorEvent, A]] = {
    val body = writeToString(event)(using RequestEvent.codec)

    val future = AnswerFuture[A](pipelines.getPipeline(event))

    answerCache.put(event.requestId, future)

    val request = HttpHelper.buildPost(nextHop, "/api/v1/async-request", body)
    HttpHelper
      .sendAsync(client, request, nextHop)
      .flatMap { response =>
        if (response.statusCode() == 200) {
          logger.debug(s"Successfully started waiting for answer for $event")
          future.await().map { e =>
            logger.debug(s"Received answer for $event: $e")
            e
          }
        } else {
          logger.error(s"Failed to send message $event to $nextHop, removing answer future")
          answerCache.remove(event.requestId)
          Future.failed(new Exception(s"Failed to send message $event to $nextHop"))
        }
      }
  }

  private class AnswerFuture[A <: AnswerEvent[V, P]](pipelineHandler: BroadcastPipelineHandler[A]) {

    private val promise = Promise[Either[ErrorEvent, A]]()
    private val writeLock = ReentrantLock()
    private var state = Option.empty[TerminateOrContinue[A]]
    def receive(answer: Either[ErrorEvent, A]): Unit = {
      writeLock.lock()
      try {
        state match
          case Some(Continue(existing)) =>
            pipelineHandler.handle(existing, answer) match
              case Terminate(answer)      => promise.complete(Try(answer))
              case continue @ Continue(_) => state = Some(continue)
          case Some(_) => throw AssertionError("Future received answer after close ")
          case None =>
            pipelineHandler.handleInit(answer) match
              case Terminate(answer)      => promise.complete(Try(answer))
              case continue @ Continue(_) => state = Some(continue)
      } finally {
        writeLock.unlock()
      }
    }

    def await(): Future[Either[ErrorEvent, A]] = promise.future
  }

  override def sendObserverUpdate(update: NodeInfoUpdate): Unit =
    observerAddress.foreach(HttpHelper.sendObserverUpdate(client, _, update))
}

object AsyncHttpNetworkAdapter extends NetworkAdapter.Factory {
  override def create[V: JsonValueCodec, P: JsonValueCodec](
      bindAddress: InetSocketAddress,
      observerAddress: Option[InetSocketAddress],
      onReceive: EventHandler[V, P],
  )(using logger: Logger): NetworkAdapter[V, P] =
    new AsyncHttpNetworkAdapter[V, P](bindAddress, observerAddress, onReceive, new BroadcastPipelines.TakeFirst)
}
