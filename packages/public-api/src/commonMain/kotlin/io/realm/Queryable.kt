package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

// Interface for collections that can be queried by Realm
// Question: Is having this interface just confusing since it doesn't include the
// `observe()` methods and there is a circular dependency with RealmQuery
interface Queryable<E> {
    fun filter(filter: String, vararg arguments: Any?): RealmQuery<E>
    fun sort(field: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<E>
    fun sort(fieldName1: String?, sortOrder1: Sort = Sort.ASCENDING, fieldName2: String, sortOrder2: Sort = Sort.ASCENDING): RealmResults<E>
    fun sort(fieldNames: Array<String?>, sortOrders: Array<Sort>): RealmResults<E>
    fun distinct(field: String): RealmQuery<E>

    fun count(): Flow<Long>
    fun min(property: String): Flow<Number?>
    fun max(property: String): Flow<Number?>
    fun sum(property: String): Flow<Number?>
    fun average(property: String): Flow<Double?>
    fun maxDate(property: String): Flow<Instant?>
    fun minDate(property: String): Flow<Instant?>
}