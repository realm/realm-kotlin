package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class RealmQuery<E>: Queryable<E> {

    // Synchronous queries? Do we even need these?
    // I would assume the primary use case is buisiness logic s
    // Transactions are suspendable, so offering synchronous queries only
    // seem to promote "bad" patterns of doing one-shot queries
    // Suggestion: Leave this out in the initial design and see if a need arises

    // fun findAll(): RealmResults<E> { TODO() }
    // fun findFirst(): E? { TODO() }
    // suspend fun findAll(): RealmResults<E> { TODO() }
    // suspend fun findFirst(): E? { TODO() }

    override fun filter(filter: String, vararg arguments: Any?): RealmQuery<E> { TODO() }
    override fun sort(field: String, sortOrder: Sort): RealmQuery<E> { TODO() }
    override fun sort(fieldName1: String?, sortOrder1: Sort, fieldName2: String, sortOrder2: Sort): RealmResults<E> { TODO() }
    override fun sort(fieldNames: Array<String?>, sortOrders: Array<Sort>): RealmResults<E> { TODO() }
    override fun distinct(field: String): RealmQuery<E> { TODO() }

    override fun count(): Flow<Long> { TODO() }
    override fun min(property: String): Flow<Number?> { TODO() }
    override fun max(property: String): Flow<Number?> { TODO() }
    override fun sum(property: String): Flow<Number?> { TODO() }
    override fun average(property: String): Flow<Double?> { TODO() }
    override fun maxDate(property: String): Flow<Instant?> { TODO() }
    override fun minDate(property: String): Flow<Instant?> { TODO() }

    fun addChangeListener(listener: (change: OrderedCollectionChange<E, RealmResults<E>>) -> Unit): Cancellable { TODO() }
    fun cancelAllChangeListeners() { TODO() }

    // Coroutine queries
    fun observe(): Flow<RealmResults<E>> { TODO() }
    fun observeChangesets(): Flow<OrderedCollectionChange<E, RealmResults<E>>> { TODO() }

    fun observeFirst(): Flow<E?> { TODO() }
}