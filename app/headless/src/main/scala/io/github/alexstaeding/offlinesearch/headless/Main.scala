package io.github.alexstaeding.offlinesearch.headless

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.alexstaeding.offlinesearch.meta.*
import io.github.alexstaeding.offlinesearch.network.*
import org.apache.logging.log4j.{LogManager, Logger}

implicit val idSpace: NodeIdSpace = NodeIdSpace(4)
implicit val logger: Logger = LogManager.getLogger("main")

@main
def main(): Unit = {
  
}