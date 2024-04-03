package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.message.Index
import scalapb.GeneratedMessageCompanion
import scalapb.GeneratedMessage

// TODO: Make generic for T = search type
trait Network[T] {

  def send(peer: Int): Unit
  
  trait Factory {
    def create(protos: Iterable[? <: GeneratedMessageCompanion[T]]): Network[T]
  }
}
