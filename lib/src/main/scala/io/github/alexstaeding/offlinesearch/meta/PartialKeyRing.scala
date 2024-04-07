package io.github.alexstaeding.offlinesearch.meta

trait PartialKeyRing[T] {
  val root: PartialKey[T]
  val degree: Int
}
