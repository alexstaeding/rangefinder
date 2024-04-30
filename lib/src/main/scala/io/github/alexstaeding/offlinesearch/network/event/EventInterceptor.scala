package io.github.alexstaeding.offlinesearch.network.event

trait EventInterceptor[V] {
  val request: RequestEvent
  def answer[A <: AnswerEvent: AnswerEvent.SimpleFactory](event: A): Unit
  def answer[A[_] <: AnswerEvent: AnswerEvent.ParameterizedFactory](event: A[V]): Unit
}
