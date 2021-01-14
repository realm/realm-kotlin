package io.realm

import io.realm.base.BaseRealmModel

interface OrderedRealmCollection<E: BaseRealmModel> : List<E>, RealmCollection<E>, Queryable<E> {
    fun first(): E
    fun first(defaultValue: E?): E?
    fun last(): E
    fun last(defaultValue: E?): E?
    fun deleteFromRealm(location: Int)
    fun deleteFirstFromRealm(): Boolean
    fun deleteLastFromRealm(): Boolean
    // fun createSnapshot(): OrderedRealmCollectionSnapshot<E>?
}