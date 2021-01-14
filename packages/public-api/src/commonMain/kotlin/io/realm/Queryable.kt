package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow

// Interface for collections that can be queried by Realm
interface Queryable<E: BaseRealmModel> {
    fun filter(filter: String, vararg arguments: Any?): RealmQuery<E> { TODO() }
    fun sort(field: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<E> { TODO() }
    fun sort(fieldName1: String?, sortOrder1: Sort = Sort.ASCENDING, fieldName2: String, sortOrder2: Sort = Sort.ASCENDING): RealmResults<E>? { TODO() }
    fun sort(fieldNames: Array<String?>, sortOrders: Array<Sort>): RealmResults<E> { TODO() }
    fun distinct(field: String): RealmQuery<E> { TODO() }
}