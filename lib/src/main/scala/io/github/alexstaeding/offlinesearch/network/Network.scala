package io.github.alexstaeding.offlinesearch.network

// TODO: Make generic for T = search type
trait Network[T, P] {

  def send(peer: Int): Unit
}
