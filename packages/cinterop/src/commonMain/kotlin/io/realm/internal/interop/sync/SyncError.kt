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
) {
    // Constructor used by JNI so we avoid creating to many objects on the JNI side.
    constructor(
        category: Int,
        value: Int,
        message: String,
        detailedMessage: String?,
        isFatal: Boolean,
        isUnrecognizedByClient: Boolean
    ) : this(
        SyncErrorCode(SyncErrorCodeCategory.fromInt(category), value, message),
        detailedMessage,
        isFatal,
        isUnrecognizedByClient
    )
}
