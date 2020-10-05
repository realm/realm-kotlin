package io.realm

import kotlinx.coroutines.flow.Flow

/**
 * An reactive optional wrapper for Realm query results.
 */
class RealmOptional<T: RealmModel> {
    fun get(): T? { TODO() }
    suspend fun observe(listener: (T) -> Unit): Flow<T?> { TODO() }
}