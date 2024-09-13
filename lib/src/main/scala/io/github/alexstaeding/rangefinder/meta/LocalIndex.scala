package io.github.alexstaeding.rangefinder.meta

import io.github.alexstaeding.rangefinder.crdt.{GrowOnlyExpiryMap, SortedGrowOnlyExpiryMultiMap}
import io.github.alexstaeding.rangefinder.network.{IndexEntry, NodeId}

import java.time.OffsetDateTime
import java.util.concurrent.locks.ReentrantLock
import scala.collection.immutable.{ListMap, TreeMap}
import scala.collection.mutable
class LocalIndex[V: Ordering: PartialKeyMatcher, P] {
  private val lock = ReentrantLock()
  private var values: Map[NodeId, SortedGrowOnlyExpiryMultiMap[V, IndexEntry.Value[V, P]]] = new ListMap
  private var funnels: Map[NodeId, GrowOnlyExpiryMap[IndexEntry.Funnel[V]]] = new ListMap

  private def searchValues(targetId: NodeId, searchKey: PartialKey[V], now: OffsetDateTime): Seq[IndexEntry[V, P]] = {
    values.get(targetId) match
      case Some(value) =>
        value
          .range(searchKey.startInclusive, searchKey.endExclusive)
          .flatMap { (_, m) => m }
          .filter { (_, expiry) => expiry.isAfter(now) }
          .map { (entry, _) => entry }
          .toList
      case None => Seq.empty
  }

  private def searchFunnels(targetId: NodeId, searchKey: PartialKey[V], now: OffsetDateTime): Seq[IndexEntry[V, P]] = {
    funnels.get(targetId) match
      case Some(value) =>
        value
          .filter { (_, expiry) => expiry.isAfter(now) }
          .map { (funnel, _) => funnel }
          .filter { funnel => funnel.searchKey.contains(searchKey) }
          .toList
      case None => Seq.empty
  }

  def search(targetId: NodeId, searchKey: PartialKey[V], now: OffsetDateTime): Seq[IndexEntry[V, P]] = {
    searchValues(targetId, searchKey, now) ++ searchFunnels(targetId, searchKey, now)
  }

  /** Performs a BFS on all provided funnels.
    */
  private def funnelBfs(
      targetId: NodeId,
      searchKey: PartialKey[V],
      funnels: Seq[IndexEntry.Funnel[V]],
      now: OffsetDateTime,
  ): Seq[IndexEntry[V, P]] = {

    case class PathEntry(funnel: IndexEntry.Funnel[V], prev: Option[PathEntry])

    val rootEntry = PathEntry(IndexEntry.Funnel(targetId, searchKey), None)
    val queue = mutable.ArrayDeque.from(funnels.map { f => PathEntry(f, Some(rootEntry)) })
    val results = new mutable.ArrayBuffer[IndexEntry[V, P]]

    while (queue.nonEmpty) {
      val current = queue.removeHead()
      // check for cycles
      if (
        Seq
          .unfold(current) { c => c.prev.map { p => (p, p) } }
          .forall { p => p.funnel.targetId == current.funnel.targetId }
      ) {
        search(current.funnel.targetId, current.funnel.searchKey, now).foreach {
          // resolve funnels that point to the local index
          case f: IndexEntry.Funnel[V]   => queue.addOne(PathEntry(f, Some(current)))
          case v: IndexEntry.Value[V, P] => results.addOne(v)
        }
        // funnel to remote index
        // additional search is not performed at this time.
        // instead, aliases are sent in result set to the query initiator
        results.addOne(current.funnel)
      }
    }

    results.toSeq
  }

  private def searchIndexGroup(targetId: NodeId, searchKey: PartialKey[V]): Seq[IndexEntry[V, P]] = {
    val now = OffsetDateTime.now()
    val localEntries = search(targetId, searchKey, now)
    val funnelResults = funnelBfs(targetId, searchKey, localEntries.collect { case x: IndexEntry.Funnel[V] => x }, now)
    val localResults = localEntries.collect { case x: IndexEntry.Value[V, P] => x }

    localResults ++ funnelResults
  }

  private def putValue(targetId: NodeId, value: IndexEntry.Value[V, P]): Unit = {
    val one = SortedGrowOnlyExpiryMultiMap.ofOne(value.value, value)
    lock.lock()
    values = values + (targetId -> SortedGrowOnlyExpiryMultiMap.lattice.merge(
      SortedGrowOnlyExpiryMultiMap.cleaned(values.getOrElse(targetId, new TreeMap)),
      one,
    ))
    lock.unlock()
  }

  private def putFunnel(targetId: NodeId, funnel: IndexEntry.Funnel[V]): Unit = {
    val one = GrowOnlyExpiryMap.ofOne(funnel)
    lock.lock()
    funnels = funnels + (targetId -> GrowOnlyExpiryMap.lattice.merge(
      GrowOnlyExpiryMap.cleaned(funnels.getOrElse(funnel.targetId, new ListMap)),
      one,
    ))
    lock.unlock()
  }

  def put(targetId: NodeId, entry: IndexEntry[V, P]): Unit =
    entry match
      case f: IndexEntry.Funnel[V]   => putFunnel(targetId, f)
      case v: IndexEntry.Value[V, P] => putValue(targetId, v)

  def getIds: Seq[NodeId] =
    values.to(LazyList).filter(_._2.nonEmpty).map(_._1) ++ funnels.to(LazyList).filter(_._2.nonEmpty).map(_._1)

}
