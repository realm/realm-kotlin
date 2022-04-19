package io.realm.internal.interop.sync

import realm_wrapper.realm_sync_error_category

actual enum class SyncErrorCodeCategory(val nativeValue: realm_sync_error_category) {
    RLM_SYNC_ERROR_CATEGORY_CLIENT(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    RLM_SYNC_ERROR_CATEGORY_SESSION(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SESSION),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN(realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    actual companion object {

        actual fun fromInt(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue.value.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category: $nativeValue")
        }

        fun of(nativeValue: realm_sync_error_category): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category: $nativeValue")
        }
    }
}
