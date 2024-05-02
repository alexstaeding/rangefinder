package io.github.alexstaeding.offlinesearch.network

trait EventInterceptor[V] {
  val request: RequestEvent[V]
  def answer[A <: AnswerEvent[V]](event: A): Unit
}
