package io.github.alexstaeding.rangefinder.network

sealed trait TerminateOrContinue[A <: AnswerEvent[?, ?]] extends Product
case class Terminate[A <: AnswerEvent[?, ?]](answer: Either[ErrorEvent, A]) extends TerminateOrContinue[A]
case class Continue[A <: AnswerEvent[?, ?]](answer: Either[ErrorEvent, A]) extends TerminateOrContinue[A]

trait BroadcastPipelineHandler[A <: AnswerEvent[?, ?]] {
  val timeoutMillis: Int
  def handleInit(answer: Either[ErrorEvent, A]): TerminateOrContinue[A]
  def handle(existing: Either[ErrorEvent, A], next: Either[ErrorEvent, A]): TerminateOrContinue[A]
}

object BroadcastPipelineHandler {
  class TakeFirst[A <: AnswerEvent[?, ?]] extends BroadcastPipelineHandler[A] {
    val timeoutMillis: Int = 0
    def handleInit(answer: Either[ErrorEvent, A]): TerminateOrContinue[A] = Terminate(answer)
    // will not occur
    def handle(existing: Either[ErrorEvent, A], next: Either[ErrorEvent, A]): TerminateOrContinue[A] = Terminate(existing)
  }
}

trait BroadcastPipelines[V, P] {
  val ping: BroadcastPipelineHandler[PingAnswerEvent]
  val findNode: BroadcastPipelineHandler[FindNodeAnswerEvent]
  val search: BroadcastPipelineHandler[SearchAnswerEvent[V, P]]
  val storeValue: BroadcastPipelineHandler[StoreValueAnswerEvent]
}

object BroadcastPipelines {
  class TakeFirst[V, P] extends BroadcastPipelines[V, P] {
    override val ping: BroadcastPipelineHandler[PingAnswerEvent] = new BroadcastPipelineHandler.TakeFirst
    override val findNode: BroadcastPipelineHandler[FindNodeAnswerEvent] = new BroadcastPipelineHandler.TakeFirst
    override val search: BroadcastPipelineHandler[SearchAnswerEvent[V, P]] = new BroadcastPipelineHandler.TakeFirst
    override val storeValue: BroadcastPipelineHandler[StoreValueAnswerEvent] = new BroadcastPipelineHandler.TakeFirst
  }
}

extension [V, P](pipelines: BroadcastPipelines[V, P]) {
  def getPipeline[A <: AnswerEvent[V, P], R <: RequestEvent[V, P] { type Answer <: A }](event: R): BroadcastPipelineHandler[A] =
    event match
      case _: PingEvent             => pipelines.ping.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: FindNodeEvent         => pipelines.findNode.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: SearchEvent[V, P]     => pipelines.search.asInstanceOf[BroadcastPipelineHandler[A]]
      case _: StoreValueEvent[V, P] => pipelines.storeValue.asInstanceOf[BroadcastPipelineHandler[A]]
}
