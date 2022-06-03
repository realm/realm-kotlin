package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import io.realm.kotlin.internal.interop.realm_sync_session_resync_mode_e

actual enum class SyncSessionResyncMode(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_SESSION_RESYNC_MODE_MANUAL(realm_sync_session_resync_mode_e.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL),
    RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL(realm_sync_session_resync_mode_e.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL);

    actual companion object {
        actual fun fromInt(nativeValue: Int): SyncSessionResyncMode {
            for (value in SyncSessionResyncMode.values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown session resync mode: $nativeValue")
        }
    }
}
