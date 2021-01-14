package io.realm

import io.realm.base.BaseRealm
import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow

class RealmQuery<E: BaseRealmModel>: Queryable<E> {

    // Synchronous queries
    fun findAll(): RealmResults<E> { TODO() }
    fun findFirst(): E? { TODO() }

    // Async queries
    fun observe(): Flow<RealmResults<E>> { TODO() }
    fun observeFirst(): Flow<E?> { TODO() }
}