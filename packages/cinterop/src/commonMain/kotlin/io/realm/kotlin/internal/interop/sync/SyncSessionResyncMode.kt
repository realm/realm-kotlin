package io.realm.kotlin.internal.interop.sync

expect enum class SyncSessionResyncMode {
    RLM_SYNC_SESSION_RESYNC_MODE_MANUAL,
    RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL;

    companion object {
        fun fromInt(nativeValue: Int): SyncSessionResyncMode
    }
}
