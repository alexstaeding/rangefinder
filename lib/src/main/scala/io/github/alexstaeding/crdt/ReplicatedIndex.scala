
package io.github.alexstaeding.crdt


type ReplicatedIndex = Map[String, ReplicatedCounter]

object ReplicatedIndex {

  val zero: ReplicatedIndex = Map.empty

  given lattice: Lattice[ReplicatedIndex] = Lattice.mapLattice

  def create(document: String): ReplicatedIndex = {
    document.split("\\W+").groupBy(identity)
      .view.mapValues(_.length).mapValues(ReplicatedCounter(_)).toMap
  }
}
