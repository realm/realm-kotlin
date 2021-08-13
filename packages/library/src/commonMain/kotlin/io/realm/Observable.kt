package io.realm

import kotlinx.coroutines.flow.Flow

/**
 * TODO
 */
interface Observable<T> {
    fun observe(): Flow<T>
}
