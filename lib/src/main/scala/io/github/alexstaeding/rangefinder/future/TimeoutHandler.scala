package io.github.alexstaeding.rangefinder.future

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.{Timer, TimerTask}
import scala.concurrent.*
import scala.concurrent.duration.Duration

private val timer = new Timer(true)

class TimeoutHandler(val duration: Duration) {
  private val timeoutPromise = Promise[Nothing]()
  private var currentTask: Option[TimerTask] = None
  private val lock = ReentrantLock()

  def bump(): Unit = {
    lock.lock()
    currentTask.foreach(_.cancel())
    val task = new TimerTask {
      def run(): Unit = timeoutPromise.tryFailure(new TimeoutException(s"Future timed out after ${duration.toMillis}ms"))
    }
    timer.schedule(task, duration.toMillis)
    currentTask = Some(task)
    lock.unlock()
  }

  def withTimeout[T](future: Future[T])(using ec: ExecutionContext): Future[T] = {
    Future.firstCompletedOf(List(future, timeoutPromise.future))
  }
}

extension [T](future: Future[T])(using ec: ExecutionContext) {
  private def withTimeout(duration: Duration): Future[T] = {
    val handler = TimeoutHandler(duration)
    handler.bump()
    handler.withTimeout(future)
  }

  def withTimeout[U >: T](after: Duration, default: => U): Future[U] =
    withTimeout(after).recover { case _: TimeoutException => default }
}
