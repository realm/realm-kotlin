package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class RealmQuery<E> {

    // Synchronous queries? We only need these for Java support?
    //
    // For Kotlin I would assume the primary use case is business logic which should mostly reside
    // inside transactions.
    // Transactions are suspendable, so offering synchronous queries only
    // seem to promote "bad" patterns of doing one-shot queries
    // Suggestion: Leave this out in the initial design and see if a need arises
    fun findAllSync(): RealmResults<E> { TODO() }
    fun findFirstSync(): E? { TODO() }

    fun filter(filter: String, vararg arguments: Any?): RealmQuery<E> { TODO() }
    fun sort(field: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<E> { TODO() }
    fun sort(fieldName1: String?, sortOrder1: Sort, fieldName2: String, sortOrder2: Sort): RealmResults<E> { TODO() }
    fun sort(fieldNames: Array<String?>, sortOrders: Array<Sort>): RealmResults<E> { TODO() }
    fun distinct(field: String): RealmQuery<E> { TODO() }

    fun count(): Flow<Long> { TODO() }
    fun min(property: String): Flow<Number?> { TODO() }
    fun max(property: String): Flow<Number?> { TODO() }
    fun sum(property: String): Flow<Number?> { TODO() }
    fun average(property: String): Flow<Double?> { TODO() }

    // How to represent dates here?
    fun maxDate(property: String): Flow<Instant?> { TODO() }
    fun minDate(property: String): Flow<Instant?> { TODO() }

    fun addChangeListener(listener: (change: OrderedCollectionChange<E, RealmResults<E>>) -> Unit): Cancellable { TODO() }
    fun cancelAllChangeListeners() { TODO() }

    // Avoid having to do `observe().first()`. Especially `query.observeFirst().first()` looks weird.
    suspend fun findAll(): RealmResults<E> { TODO() }
    suspend fun findFirst(): E? { TODO() }

    // Observe queries
    fun observe(): Flow<RealmResults<E>> { TODO() }
    fun observeChangesets(): Flow<OrderedCollectionChange<E, RealmResults<E>>> { TODO() }

    fun observeFirst(): Flow<E?> { TODO() }
    fun observeFirstChangesets(): Flow<ObjectChange<E>> { TODO() }
}