package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_sync_error_category_e

actual enum class SyncErrorCodeCategory(override val nativeValue: Int): NativeEnumerated {
    RLM_SYNC_ERROR_CATEGORY_CLIENT(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    RLM_SYNC_ERROR_CATEGORY_SESSION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SESSION),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    actual companion object {
        actual fun fromInt(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category value: $nativeValue")
        }

        @JvmStatic
        fun of(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category value: $nativeValue")
        }
    }
}
