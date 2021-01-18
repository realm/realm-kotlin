package io.realm

import io.realm.base.BaseRealmModel

/**
 * Interface for top-level RealmObjects. All model classes should either implement this
 * or [EmbeddedRealmObject]
 */
interface RealmObject<E: RealmObject<E>>: BaseRealmModel<E> {
    fun getRealm(): Realm? { TODO() }
}