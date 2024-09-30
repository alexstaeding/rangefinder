package io.github.alexstaeding.rangefinder.future

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.{Timer, TimerTask}
import scala.concurrent.*
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

private val timer = new Timer(true)

class TimeoutHandler[T](val duration: Duration) {
  private val timeoutPromise = Promise[T]()
  private var currentTask: Option[TimerTask] = None
  private val taskLock = ReentrantLock()

  private val defaultResult = new AtomicReference[Try[T]]

  def bump(): Unit = {
    taskLock.lock()
    currentTask.foreach(_.cancel())
    val task = new TimerTask {
      def run(): Unit = timeoutPromise.complete(defaultResult.get())
    }
    timer.schedule(task, duration.toMillis)
    currentTask = Some(task)
    taskLock.unlock()
  }

  def setDefaultResult(result: Try[T]): Unit = {
    defaultResult.set(result)
  }

  def setDefaultTimeout(): Unit = {
    defaultResult.set(Failure(new TimeoutException(s"Future timed out after ${duration.toMillis}ms")))
  }

  def withTimeout(future: Future[T])(using ec: ExecutionContext): Future[T] = {
    future.onComplete { _ =>
      taskLock.lock()
      currentTask.foreach(_.cancel())
      taskLock.unlock()
    }
    Future.firstCompletedOf(List(future, timeoutPromise.future))
  }
}

extension [T](future: Future[T])(using ec: ExecutionContext) {
  def withTimeout(duration: Duration): Future[T] = {
    val handler = TimeoutHandler[T](duration)
    handler.setDefaultTimeout()
    handler.bump()
    handler.withTimeout(future)
  }

  def withTimeoutAndDefault[U >: T](after: Duration, default: => U): Future[U] =
    withTimeout(after).recover { case _: TimeoutException => default }
}
