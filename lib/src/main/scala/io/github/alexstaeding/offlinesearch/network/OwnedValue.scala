package io.github.alexstaeding.offlinesearch.network

private case class OwnedValue[V](
    owner: NodeId,
    value: V,
    path: String, // TODO: Custom info to return to finder
    // TODO: Expiry
)

object OwnedValue {
  given ordering[V: Ordering]: Ordering[OwnedValue[V]] =
    (x: OwnedValue[V], y: OwnedValue[V]) => summon[Ordering[V]].compare(x.value, y.value)
}
