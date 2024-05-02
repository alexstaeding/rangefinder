package io.github.alexstaeding.offlinesearch.network

import java.util

case class NodeId(bytes: Array[Byte])(using val space: NodeIdSpace) {
  require(bytes.length == space.size, s"NodeId should be of length ${space.size}, is ${bytes.length}")
  override def hashCode(): Int = util.Arrays.hashCode(bytes)
  override def equals(obj: Any): Boolean = obj match {
    case obj: NodeId => obj.canEqual(this) && util.Arrays.equals(bytes, obj.bytes)
    case _           => false
  }
  override def canEqual(that: Any): Boolean = that.isInstanceOf[NodeId]
  override def toString: String = bytes.map("%02x".format(_)).mkString.grouped(4).mkString(" ")
}

object NodeId {
  case class DistanceOrdering(targetId: NodeId) extends Ordering[NodeId] {
    override def compare(x: NodeId, y: NodeId): Int = NaturalOrdering.compare(x.xor(targetId), y.xor(targetId))
  }
  case object NaturalOrdering extends Ordering[NodeId] {
    override def compare(x: NodeId, y: NodeId): Int = BigInt(x.bytes) compare BigInt(y.bytes)
  }

  extension (self: NodeId) {
    def xor(other: NodeId): NodeId = {
      require(self.space == other.space, "Node IDs must be of the same length")
      NodeId(self.bytes.zip(other.bytes).map((x, y) => (x ^ y).toByte))(using self.space)
    }
  }
}

/** The size in bytes of the id keyspace
  */
case class NodeIdSpace(size: Int)
