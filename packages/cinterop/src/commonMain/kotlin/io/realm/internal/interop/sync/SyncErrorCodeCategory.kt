package io.realm.internal.interop.sync

/**
 * Wrapper for C-API `realm_sync_error_code_category`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2396
 */
expect enum class SyncErrorCodeCategory {
    RLM_SYNC_ERROR_CATEGORY_CLIENT,
    RLM_SYNC_ERROR_CATEGORY_CONNECTION,
    RLM_SYNC_ERROR_CATEGORY_SESSION,
    RLM_SYNC_ERROR_CATEGORY_SYSTEM,
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN;
}
