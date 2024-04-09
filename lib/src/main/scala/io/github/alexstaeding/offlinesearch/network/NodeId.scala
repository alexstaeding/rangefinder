package io.github.alexstaeding.offlinesearch.network

import java.util

case class NodeId(bytes: Array[Byte]) {
  override def hashCode(): Int = util.Arrays.hashCode(bytes)
}
