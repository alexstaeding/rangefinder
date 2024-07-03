package io.github.alexstaeding.offlinesearch.types.simple

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.meta.*
import io.github.alexstaeding.offlinesearch.network.{HashingAlgorithm, NodeId, NodeIdSpace}

import java.security.MessageDigest

case class StringIndex(data: String)

object StringIndex {
  given codec: JsonValueCodec[StringIndex] = JsonCodecMaker.make
  given hashingAlgorithm(using idSpace: NodeIdSpace): HashingAlgorithm[StringIndex] = (value: PartialKey[StringIndex]) => {
    val hash = MessageDigest
      .getInstance("SHA1")
      .digest(value.startInclusive.data.getBytes("UTF-8") ++ value.endExclusive.data.getBytes("UTF-8"))
    NodeId(hash.take(idSpace.size))
  }
  given universe: PartialKeyUniverse[StringIndex] with
    override def getRootKey(value: StringIndex): PartialKey[StringIndex] =
      StringPrefixPartialKeyUniverse.getRootKey(value.data).map(StringIndex.apply)
    override def getOverlappingRootKeys(key: PartialKey[StringIndex]): Seq[PartialKey[StringIndex]] =
      StringPrefixPartialKeyUniverse.getOverlappingRootKeys(key.map(_.data)).map(_.map(StringIndex.apply))

  given matcher: PartialKeyMatcher[StringIndex] with
    extension (partialKey: PartialKey[StringIndex])
      override def matches(search: StringIndex): Boolean = StringPrefixPartialKeyMatcher.matches(partialKey.map(_.data))(search.data)
  given ordering: Ordering[StringIndex] = Ordering.by(_.data)
}
