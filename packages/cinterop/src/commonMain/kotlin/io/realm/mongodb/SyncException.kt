package io.realm.mongodb

/**
 * TODO contains only the serialized message from the C-API
 *  align with https://github.com/realm/realm-kotlin/issues/524
 */
class SyncException(
    message: String
) : Exception(message)
