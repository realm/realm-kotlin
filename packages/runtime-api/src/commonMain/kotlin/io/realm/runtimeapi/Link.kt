package io.realm.runtimeapi

// Could potentially work without an open realm - sort of non-live object-ish
// FIXME Do we need a type variable here?
class Link(
    var objKey: Long,
    var tableKey: Long,
)
