package io.github.alexstaeding.rangefinder.types.simple

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

case class StringPayload(data: String)

object StringPayload {
  given codec: JsonValueCodec[StringPayload] = JsonCodecMaker.make
}
