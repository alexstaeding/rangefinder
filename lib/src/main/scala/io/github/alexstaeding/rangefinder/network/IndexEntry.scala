package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.rangefinder.meta.PartialKey

sealed trait IndexEntry[+V, +P]

object IndexEntry {
  final case class Funnel[V](targetId: NodeId, search: PartialKey[V]) extends IndexEntry[V, Nothing]
    
  final case class Value[V, P](owner: NodeId, value: V, path: P) extends IndexEntry[V, P]

  object Value {
    given codec[V: JsonValueCodec, P: JsonValueCodec]: JsonValueCodec[Value[V, P]] = JsonCodecMaker.make

    given ordering[V: Ordering, P]: Ordering[IndexEntry.Value[V, P]] =
      (x: IndexEntry.Value[V, P], y: IndexEntry.Value[V, P]) => summon[Ordering[V]].compare(x.value, y.value)
  }
}
