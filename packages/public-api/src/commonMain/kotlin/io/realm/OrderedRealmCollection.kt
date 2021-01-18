package io.realm

import io.realm.base.BaseRealmModel

interface OrderedRealmCollection<E> : List<E>, RealmCollection<E>, Queryable<E> {
    fun first(): E
    fun firstOrDefault(defaultValue: E?): E?
    fun last(): E
    fun lastOrDefault(defaultValue: E?): E?
    fun deleteFromRealm(location: Int)
    fun deleteFirstFromRealm(): Boolean
    fun deleteLastFromRealm(): Boolean
//     fun createSnapshot(): OrderedRealmCollectionSnapshot<E>?
}