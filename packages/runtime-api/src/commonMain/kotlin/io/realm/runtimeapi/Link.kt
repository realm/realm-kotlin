package io.realm.runtimeapi

// Could potentially work without an open realm - sort of non-live object-ish
// FIXME Do we need a type variable here?
// TODO OPTIMIZE Consider just wrapping a native pointer?
class Link(
    var tableKey: Long,
    // Could potentially be narrowed to Int, but Swig automatically returns long for the underlying
    // uint32_t, while cinterop uses UInt!?
    var objKey: Long,
)
