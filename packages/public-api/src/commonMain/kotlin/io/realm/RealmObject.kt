package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow

interface RealmObject<E: RealmObject<E>>: BaseRealmModel {
    fun deleteFromRealm() { TODO() }
    fun getRealm(): Realm? { TODO() }

    fun addChangeListener(listener: RealmChangeListener<Realm>) { TODO() }
    fun removeChangeListener(listener: RealmChangeListener<Realm>)  { TODO() }
    fun removeAllListeners() { TODO() }

    fun observe(): Flow<E?> { TODO() }
    // fun observeChangesets: Flow<ObjectChange>
}