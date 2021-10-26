package io.realm.mongodb

class SyncException(
    val errorCode: SyncErrorCode,
    val detailedMessage: String,
    val fatal: Boolean,
    val unrecognizedByClient: Boolean,

    // TODO user_info_map?
) : Exception(detailedMessage)

class SyncErrorCode(
    val value: Int,
    val message: String,
    coreCategory: Short
) {
    val category: SyncErrorCategory = SyncErrorCategory.fromValue(coreCategory)
}

expect enum class SyncErrorCategory {
    RLM_SYNC_ERROR_CATEGORY_CLIENT,
    RLM_SYNC_ERROR_CATEGORY_CONNECTION,
    RLM_SYNC_ERROR_CATEGORY_SESSION,
    RLM_SYNC_ERROR_CATEGORY_SYSTEM,
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN;

    companion object {
        fun fromValue(value: Short): SyncErrorCategory
    }
}
