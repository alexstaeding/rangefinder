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
}

/** The size in bytes of the id keyspace
  */
case class NodeIdSpace(size: Int)
