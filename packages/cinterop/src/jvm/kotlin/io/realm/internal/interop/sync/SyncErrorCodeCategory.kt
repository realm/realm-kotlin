package io.realm.internal.interop.sync

import io.realm.internal.interop.realm_sync_error_category_e

actual enum class SyncErrorCodeCategory(val nativeValue: Int) {
    CLIENT(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    CONNECTION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    SESSION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SESSION),
    SYSTEM(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    UNKNOWN(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    companion object {
        @JvmStatic
        fun of(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown value: $nativeValue")
        }
    }
}
