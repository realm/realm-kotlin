package io.realm.internal.interop.sync

/**
 * Wrapper for C-API `realm_sync_error_code`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2997
 */
data class SyncErrorCode(
    val category: SyncErrorCodeCategory,
    val value: Int,
    val message: String
)
