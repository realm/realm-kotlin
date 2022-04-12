package io.realm.internal.interop.sync

expect enum class SyncErrorCodeCategory {
    CLIENT,
    CONNECTION,
    SESSION,
    SYSTEM,
    UNKNOWN;
}
