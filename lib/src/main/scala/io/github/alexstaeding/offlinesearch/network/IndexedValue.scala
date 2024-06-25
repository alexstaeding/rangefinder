package io.github.alexstaeding.offlinesearch.network

import io.github.alexstaeding.offlinesearch.meta.PartialKey

case class IndexedValue[V](
    partialKey: PartialKey[V],
    value: V,
)
