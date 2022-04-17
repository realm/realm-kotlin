package io.realm.internal.interop.sync

/**
 * Wrapper for C-API `realm_sync_error`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3012
 */
data class SyncError(
    val errorCode: SyncErrorCode,
    val detailedMessage: String?,
    val isFatal: Boolean,
    val isUnrecognizedByClient: Boolean
)
