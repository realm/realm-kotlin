package io.realm.kotlin.internal.interop.sync

/**
 * Wrapper for C-API `realm_sync_session_resync_mode`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3166
 */
expect enum class SyncSessionResyncMode {
    RLM_SYNC_SESSION_RESYNC_MODE_MANUAL,
    RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL;

    companion object {
        fun fromInt(nativeValue: Int): SyncSessionResyncMode
    }
}
