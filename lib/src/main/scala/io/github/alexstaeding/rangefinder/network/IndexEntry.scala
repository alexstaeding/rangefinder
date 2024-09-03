package io.github.alexstaeding.rangefinder.network

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.rangefinder.meta.{PartialKey, PartialKeyUniverse}
import org.apache.logging.log4j.Logger

sealed trait IndexEntry[+V, +P]

object IndexEntry {
  final case class Funnel[+V](targetId: NodeId, search: PartialKey[V]) extends IndexEntry[V, Nothing]

  final case class Value[+V, +P](owner: NodeId, value: V, path: P) extends IndexEntry[V, P]

  object Value {
    given codec[V: JsonValueCodec, P: JsonValueCodec]: JsonValueCodec[Value[V, P]] = JsonCodecMaker.make

    given ordering[V: Ordering, P]: Ordering[IndexEntry.Value[V, P]] =
      (x: IndexEntry.Value[V, P], y: IndexEntry.Value[V, P]) => summon[Ordering[V]].compare(x.value, y.value)
  }
}

extension [V](entry: IndexEntry[V, ?]) {
  def getRootKeys(using universe: PartialKeyUniverse[V]): Seq[PartialKey[V]] =
    entry match
      case IndexEntry.Funnel(_, search)  => universe.getOverlappingRootKeys(search)
      case IndexEntry.Value(_, value, _) => Seq(universe.getRootKey(value))

  def getRootKeysOption(using universe: PartialKeyUniverse[V], logger: Logger): Option[Seq[PartialKey[V]]] =
    try Some(getRootKeys)
    catch {
      case e: Exception =>
        logger.error(s"Failed to get root keys for entry $entry", e)
        None
    }

}
