package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Class wrapping a query.
 */
class RealmQuery<E> {

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

    // TODO: How to represent dates here?
    fun maxDate(property: String): Flow<Instant?> { TODO() }
    fun minDate(property: String): Flow<Instant?> { TODO() }

    fun addChangeListener(listener: (change: OrderedCollectionChange<E, RealmResults<E>>) -> Unit): Cancellable { TODO() }
    fun cancelAllChangeListeners() { TODO() }

    // Observe queries. Keep to minimum for first draft. Specializations can be created
    // using extension functions if needed. e.g. `observeFirst()/findAll()/findFirst()` can
    // all be created from `observe()`.
    fun observe(): Flow<OrderedCollectionChange<E, RealmResults<E>>> { TODO() }

}