package io.realm

import io.realm.base.BaseRealmModel

interface EmbeddedRealmObject<E: EmbeddedRealmObject<E>> : BaseRealmModel<E> {
    fun getRealm(): Realm? { TODO() }
}