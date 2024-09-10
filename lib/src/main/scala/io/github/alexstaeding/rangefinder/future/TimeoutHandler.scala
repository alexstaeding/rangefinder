package io.github.alexstaeding.rangefinder.future

import java.util.concurrent.CompletableFuture
import java.util.{Timer, TimerTask}
import scala.concurrent.*
import scala.concurrent.duration.Duration

private val timer = new Timer(true)

class TimeoutHandler {

}

extension [T](future: Future[T])(using ec: ExecutionContext) {
  private def withTimeout(after: Duration): Future[T] = {
    val timeoutPromise = Promise[T]()

    val task = new TimerTask {
      def run(): Unit = timeoutPromise.tryFailure(new TimeoutException(s"Future timed out after ${after.toMillis}ms"))
    }
    timer.schedule(task, after.toMillis)

    future.onComplete { _ =>
      task.cancel()
      timer.cancel()
    }

    Future.firstCompletedOf(List(future, timeoutPromise.future))
  }

  def withTimeout[U >: T](after: Duration, default: => U): Future[U] =
    withTimeout(after).recover { case _: TimeoutException => default }
}
