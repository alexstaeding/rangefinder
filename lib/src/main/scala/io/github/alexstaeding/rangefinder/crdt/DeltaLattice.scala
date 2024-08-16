package io.github.alexstaeding.rangefinder.crdt


trait DeltaLattice[V, D] {

  def createSendRequest(item: V): DeltaLattice.SendRequest[D]
  
  

}

object DeltaLattice {
  case class SendRequest[D](base: D)
}
