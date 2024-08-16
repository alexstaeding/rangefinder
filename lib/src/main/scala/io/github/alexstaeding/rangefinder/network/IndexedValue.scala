package io.github.alexstaeding.rangefinder.network

import io.github.alexstaeding.rangefinder.meta.PartialKey

case class IndexedValue[V](
    partialKey: PartialKey[V],
    value: V,
)
