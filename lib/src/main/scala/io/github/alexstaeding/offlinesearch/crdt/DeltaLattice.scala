package io.github.alexstaeding.offlinesearch.crdt


trait DeltaLattice[V, D] {

  def createSendRequest(item: V): DeltaLattice.SendRequest[D]
  
  

}

object DeltaLattice {
  case class SendRequest[D](base: D)
}
