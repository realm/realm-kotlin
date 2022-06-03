package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import realm_wrapper.realm_sync_session_resync_mode

actual enum class SyncSessionResyncMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SYNC_SESSION_RESYNC_MODE_MANUAL(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL.value),
    RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL.value);

    actual companion object {
        actual fun fromInt(nativeValue: Int): SyncSessionResyncMode {
            for (value in SyncSessionResyncMode.values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown session resync mode: $nativeValue")
        }
    }
}
