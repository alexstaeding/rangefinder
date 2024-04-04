package io.github.alexstaeding.offlinesearch.crdt

case class StringFrequencyIndex(data: Map[String, Counter] = Map.empty)

object StringFrequencyIndex {

  val zero = StringFrequencyIndex()

  // given lattice: Lattice[StringFrequencyIndex] = Lattice.mapLattice

  given lattice: Lattice[StringFrequencyIndex] = {
    (left: StringFrequencyIndex, right: StringFrequencyIndex) =>
      import Counter.lattice
      StringFrequencyIndex(Lattice.mapLattice.merge(left.data, right.data))
  }

  def create(document: String): StringFrequencyIndex = {
    val t = StringFrequencyIndex()

    val data = document
      .split("\\W+")
      .groupBy(identity)
      .view
      .mapValues(_.length)
      .mapValues(Counter(_, 0))
      .toMap

      StringFrequencyIndex(data)
  }
}
