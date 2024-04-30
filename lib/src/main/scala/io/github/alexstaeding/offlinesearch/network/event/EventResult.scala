package io.github.alexstaeding.offlinesearch.network.event

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter, ReaderConfig}
import io.github.alexstaeding.offlinesearch.network.NodeInfo

import scala.reflect.ClassTag

/** A tristate which may contain a value, or be empty, or redirect to another node.
  */
sealed trait EventResult[+V] extends Product1[V] {
  def get: V

  final def isEmpty: Boolean = this eq None

  final def isDefined: Boolean = !isEmpty

  def map[U](f: V => U): EventResult[U] = this match
    case Some(value)      => Some(f(value))
    case Redirect(target) => Redirect(target)
    case None             => None

  def flatMap[U](f: V => EventResult[U]): EventResult[U] = this match
    case Some(value)      => f(value)
    case Redirect(target) => Redirect(target)
    case None             => None
}

object EventResult {
  def apply[V](value: V): EventResult[V] = Some(value)
  def empty[V]: EventResult[V] = None
  def redirect(target: NodeInfo): EventResult[Nothing] = Redirect(target)

  given eventResultCodec[V: ClassTag](using codec: JsonValueCodec[V]): JsonValueCodec[EventResult[V]] = new JsonValueCodec[EventResult[V]] {
    override def decodeValue(in: JsonReader, default: EventResult[V]): EventResult[V] = {
      in.setMark()
      in.nextToken()
      val result = in.readKeyAsString() match {
        case "Some" =>
          in.rollbackToMark()
          Some(in.read[V](codec, "value", ReaderConfig))
        case "Redirect" =>
          in.rollbackToMark()
          Redirect(in.read[NodeInfo]("target"))
        case "None" =>
          None
      }
      in.rollbackToMark()
      result
    }

    override def encodeValue(x: EventResult[V], out: JsonWriter): Unit = x match {
      case Some(value) =>
        out.writeObjectStart()
        out.writeKey("type")
        out.writeVal("Some")
        out.writeKey("value")
        out.writeVal(value)
        out.writeObjectEnd()
      case Redirect(target) =>
        out.writeObjectStart()
        out.writeKey("type")
        out.writeVal("Redirect")
        out.writeKey("target")
        out.writeVal(target)
        out.writeObjectEnd()
      case None =>
        out.writeObjectStart()
        out.writeKey("type")
        out.writeVal("None")
        out.writeObjectEnd()
    }

    override val nullValue: EventResult[Nothing] = None
  }
}

final case class Some[+V](value: V) extends EventResult[V] {
  override def get: V = value
}

final case class Redirect(target: NodeInfo) extends EventResult[Nothing] {
  override def get: Nothing = throw new NoSuchElementException("Redirect.get")
}

case object None extends EventResult[Nothing] {
  override def get: Nothing = throw new NoSuchElementException("None.get")
}
