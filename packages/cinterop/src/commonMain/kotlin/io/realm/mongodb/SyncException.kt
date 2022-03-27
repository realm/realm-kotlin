package io.realm.mongodb

import io.realm.internal.interop.sync.SyncErrorCodeCategory

/**
 * TODO contains only the serialized message from the C-API
 *  align with https://github.com/realm/realm-kotlin/issues/524
 */
data class SyncErrorCode(
    val category: SyncErrorCodeCategory,
    val value: Int,
    val message: String
) {
}

class SyncException(message: String): Exception(message)
