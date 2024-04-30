package io.github.alexstaeding.offlinesearch.network.event

trait EventInterceptor[V] {
  val request: RequestEvent
  def answer[A <: AnswerEvent[V]](event: A)(using AnswerEvent.Factory[A, V]): Unit
}
