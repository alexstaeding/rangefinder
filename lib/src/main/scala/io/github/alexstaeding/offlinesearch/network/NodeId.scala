package io.github.alexstaeding.offlinesearch.network

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonReader, JsonValueCodec, JsonWriter}

import java.util

case class NodeId(bytes: Array[Byte])(using val space: NodeIdSpace) {
  require(bytes.length == space.size, s"NodeId should be of length ${space.size}, is ${bytes.length}")
  override def hashCode(): Int = util.Arrays.hashCode(bytes)
  override def equals(obj: Any): Boolean = obj match {
    case obj: NodeId => obj.canEqual(this) && util.Arrays.equals(bytes, obj.bytes)
    case _           => false
  }
  override def canEqual(that: Any): Boolean = that.isInstanceOf[NodeId]
  override def toString: String = toHex.grouped(4).mkString(" ")
  def toHex: String = bytes.map("%02x".format(_)).mkString
}

object NodeId {

  def generateRandom(seed: Option[Int] = None)(using idSpace: NodeIdSpace): NodeId = {
    val random = seed.map(new util.Random(_)).getOrElse(util.Random())
    val bytes = Array.fill[Byte](idSpace.size)(0)
    random.nextBytes(bytes)
    NodeId(bytes)
  }

  def zero(using idSpace: NodeIdSpace): NodeId = NodeId(Array.fill[Byte](idSpace.size)(0))

  def fromHex(hex: String)(using idSpace: NodeIdSpace): Option[NodeId] = {
    val bytes =
      try hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray
      catch case e: NumberFormatException => return None
    if (bytes.length == idSpace.size) Some(NodeId(bytes)) else None
  }

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

  given keyCodec(using NodeIdSpace): JsonKeyCodec[NodeId] = new JsonKeyCodec[NodeId] {
    override def decodeKey(in: JsonReader): NodeId = NodeId.fromHex(in.readKeyAsString()).get
    override def encodeKey(x: NodeId, out: JsonWriter): Unit = out.writeKey(x.toHex)
  }

  given valueCodec(using NodeIdSpace): JsonValueCodec[NodeId] = new JsonValueCodec[NodeId]:
    override def decodeValue(in: JsonReader, default: NodeId): NodeId = {
      val hex = in.readString(null)
      if (hex == null) default else NodeId.fromHex(hex).get
    }

    override def encodeValue(x: NodeId, out: JsonWriter): Unit = out.writeVal(x.toHex)
    override def nullValue: NodeId = NodeId.zero
}

/** The size in bytes of the id keyspace
  */
case class NodeIdSpace(size: Int)
