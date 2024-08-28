package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromStream, writeToString}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.logging.log4j.Logger

import java.io.File
import java.net.http.HttpClient.Version
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.net.{InetSocketAddress, URI}
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.io.Source
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.Try

class AsyncHttpNetworkAdapter[V: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V],
    private val pipelines: BroadcastPipelines[V],
)(using logger: Logger)
    extends NetworkAdapter[V] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val answerCache: mutable.Map[UUID, AnswerFuture[?]] = new mutable.HashMap

  private def receiveAnswer[A <: AnswerEvent[V]](answer: A): Unit = {
    answerCache.get(answer.requestId) match
      case Some(future) => future.asInstanceOf[AnswerFuture[A]].receive(answer.extractRedirect())
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
      val answer = readFromStream(exchange.getRequestBody)(using AnswerEvent.codec)
      logger.info(s"Received answer $answer to endpoint /api/v1/async-answer")
      receiveAnswer(answer)
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    },
  )

  override def send[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent[V], A]] = {
    logger.info(s"Sending message $event to $nextHop")
    val body = writeToString(event)(using RequestEvent.codec)

    val future = AnswerFuture[A](pipelines.getPipeline(event))

    answerCache.put(event.requestId, future)

    val request = HttpHelper.sendJsonPost(nextHop, "/api/v1/async-request", body)

    val response = client.send(request, BodyHandlers.ofString())
    if (response.statusCode() == 200) {
      logger.info(s"Successfully started waiting for answer for $event")
      future.await()
    } else {
      logger.error(s"Failed to send message $event to $nextHop, removing answer future")
      answerCache.remove(event.requestId)
      Future.failed(new Exception(s"Failed to send message $event to $nextHop"))
    }
  }

  private class AnswerFuture[A <: AnswerEvent[V]](pipelineHandler: BroadcastPipelineHandler[V, A]) {

    private val promise = Promise[RedirectOr[V, A]]()
    private val writeLock = ReentrantLock()
    private var state = Option.empty[TerminateOrContinue[V, A]]
    def receive(answer: RedirectOr[V, A]): Unit = {
      writeLock.lock()
      try {
        state match
          case Some(Continue(existing)) =>
            pipelineHandler.handle(existing, answer) match
              case Terminate(answer)      => promise.complete(Try(answer))
              case continue @ Continue(_) => state = Some(continue)
          case Some(_) => throw AssertionError("Future received answer after close ")
          case None    => state = Some(pipelineHandler.handleInit(answer))
      } finally {
        writeLock.unlock()
      }
    }

    def await(): Future[RedirectOr[V, A]] = promise.future
  }

  override def sendObserverUpdate(update: NodeInfoUpdate): Unit =
    observerAddress.foreach(HttpHelper.sendObserverUpdate(client, _, update))
}

sealed trait TerminateOrContinue[V, A <: AnswerEvent[V]] extends Product
case class Terminate[V, A <: AnswerEvent[V]](answer: RedirectOr[V, A]) extends TerminateOrContinue[V, A]
case class Continue[V, A <: AnswerEvent[V]](answer: RedirectOr[V, A]) extends TerminateOrContinue[V, A]

trait BroadcastPipelineHandler[V, A <: AnswerEvent[V]] {
  val timeoutMillis: Int
  def handleInit(answer: RedirectOr[V, A]): TerminateOrContinue[V, A]
  def handle(existing: RedirectOr[V, A], next: RedirectOr[V, A]): TerminateOrContinue[V, A]
}

trait BroadcastPipelines[V] {
  val ping: BroadcastPipelineHandler[V, PingAnswerEvent[V]]
  val findNode: BroadcastPipelineHandler[V, FindNodeAnswerEvent[V]]
  val search: BroadcastPipelineHandler[V, SearchAnswerEvent[V]]
  val storeValue: BroadcastPipelineHandler[V, StoreValueAnswerEvent[V]]
}

extension [V](pipelines: BroadcastPipelines[V]) {
  def getPipeline[A <: AnswerEvent[V], R <: RequestEvent[V] { type Answer <: A }](event: R): BroadcastPipelineHandler[V, A] =
    event match
      case _: PingEvent[V]       => pipelines.ping.asInstanceOf[BroadcastPipelineHandler[V, A]]
      case _: FindNodeEvent[V]   => pipelines.findNode.asInstanceOf[BroadcastPipelineHandler[V, A]]
      case _: SearchEvent[V]     => pipelines.search.asInstanceOf[BroadcastPipelineHandler[V, A]]
      case _: StoreValueEvent[V] => pipelines.storeValue.asInstanceOf[BroadcastPipelineHandler[V, A]]
}

object BroadcastPipelineHandler {}
