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

class AsyncHttpNetworkAdapter[V: JsonValueCodec, P: JsonValueCodec](
    private val bindAddress: InetSocketAddress,
    private val observerAddress: Option[InetSocketAddress],
    private val eventHandler: EventHandler[V, P],
    private val pipelines: BroadcastPipelines[V, P],
)(using logger: Logger)
    extends NetworkAdapter[V, P] {

  private val client = HttpClient.newHttpClient()
  private val server = HttpServer.create(bindAddress, 10)
  implicit val ec: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  private val answerCache: mutable.Map[UUID, AnswerFuture[?]] = new mutable.HashMap

  private def receiveAnswer[A <: AnswerEvent[V, P]](answer: A): Unit = {
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
      // Ambiguous given instances for V and P codec
      val answer =
        readFromStream(exchange.getRequestBody)(using AnswerEvent.codec(using summon[JsonValueCodec[V]], summon[JsonValueCodec[P]]))
      logger.info(s"Received answer $answer to endpoint /api/v1/async-answer")
      receiveAnswer(answer)
      exchange.sendResponseHeaders(200, 0)
      exchange.close()
    },
  )

  override def send[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](
      nextHop: InetSocketAddress,
      event: R,
  ): Future[Either[RedirectEvent, A]] = {
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

  private class AnswerFuture[A <: AnswerEvent[V, P]](pipelineHandler: BroadcastPipelineHandler[A]) {

    private val promise = Promise[RedirectOr[A]]()
    private val writeLock = ReentrantLock()
    private var state = Option.empty[TerminateOrContinue[A]]
    def receive(answer: RedirectOr[A]): Unit = {
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

    def await(): Future[RedirectOr[A]] = promise.future
  }

  override def sendObserverUpdate(update: NodeInfoUpdate): Unit =
    observerAddress.foreach(HttpHelper.sendObserverUpdate(client, _, update))
}

sealed trait TerminateOrContinue[A <: AnswerEvent[?, ?]] extends Product
case class Terminate[A <: AnswerEvent[?, ?]](answer: RedirectOr[A]) extends TerminateOrContinue[A]
case class Continue[A <: AnswerEvent[?, ?]](answer: RedirectOr[A]) extends TerminateOrContinue[A]

trait BroadcastPipelineHandler[A <: AnswerEvent[?, ?]] {
  val timeoutMillis: Int
  def handleInit(answer: RedirectOr[A]): TerminateOrContinue[A]
  def handle(existing: RedirectOr[A], next: RedirectOr[A]): TerminateOrContinue[A]
}

trait BroadcastPipelines[V, P] {
  val ping: BroadcastPipelineHandler[PingAnswerEvent]
  val findNode: BroadcastPipelineHandler[FindNodeAnswerEvent]
  val search: BroadcastPipelineHandler[SearchAnswerEvent[V, P]]
  val storeValue: BroadcastPipelineHandler[StoreValueAnswerEvent]
}

extension [V, P](pipelines: BroadcastPipelines[V, P]) {
  def getPipeline[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](event: R): BroadcastPipelineHandler[A] =
    event match
      case _: PingEvent             => pipelines.ping.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: FindNodeEvent         => pipelines.findNode.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: SearchEvent[V, P]     => pipelines.search.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: StoreValueEvent[V, P] => pipelines.storeValue.asInstanceOf[BroadcastPipelineHandler[A]]
}

object BroadcastPipelineHandler {}
