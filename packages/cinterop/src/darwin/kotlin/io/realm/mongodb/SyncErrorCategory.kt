package io.realm.mongodb

actual enum class SyncErrorCategory(val category: Int) {
    RLM_SYNC_ERROR_CATEGORY_CLIENT(realm_wrapper.realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CLIENT.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION(realm_wrapper.realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CONNECTION.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_SESSION(realm_wrapper.realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SESSION.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM(realm_wrapper.realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SYSTEM.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN(realm_wrapper.realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_UNKNOWN.value.toInt());

    actual companion object {
        actual fun fromValue(value: Short): SyncErrorCategory {
            return when (value.toInt()) {
                RLM_SYNC_ERROR_CATEGORY_CLIENT.category -> RLM_SYNC_ERROR_CATEGORY_CLIENT
                RLM_SYNC_ERROR_CATEGORY_CONNECTION.category -> RLM_SYNC_ERROR_CATEGORY_CONNECTION
                RLM_SYNC_ERROR_CATEGORY_SESSION.category -> RLM_SYNC_ERROR_CATEGORY_SESSION
                RLM_SYNC_ERROR_CATEGORY_SYSTEM.category -> RLM_SYNC_ERROR_CATEGORY_SYSTEM
                RLM_SYNC_ERROR_CATEGORY_UNKNOWN.category -> RLM_SYNC_ERROR_CATEGORY_UNKNOWN
                else -> throw IllegalArgumentException("Invalid sync error category: $value")
            }
        }
    }
}
