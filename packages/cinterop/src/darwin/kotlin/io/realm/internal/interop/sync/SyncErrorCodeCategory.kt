package io.realm.internal.interop.sync

import realm_wrapper.realm_sync_error_category

actual enum class SyncErrorCodeCategory(val nativeValue: realm_sync_error_category) {
    CLIENT(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    CONNECTION(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    SESSION(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SESSION),
    SYSTEM(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    UNKNOWN(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    companion object {
        // TODO Optimize
        fun of(nativeValue: realm_sync_error_category): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown user state: $nativeValue")
        }
    }
}