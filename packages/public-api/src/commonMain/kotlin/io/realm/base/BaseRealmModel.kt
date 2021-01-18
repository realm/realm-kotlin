package io.realm.base

import io.realm.ManageableObject
import io.realm.ObjectChange
import io.realm.RealmChangeListener
import kotlinx.coroutines.flow.Flow

/**
 * Share
 */
interface BaseRealmModel<E: BaseRealmModel<E>>: ManageableObject {
    fun deleteFromRealm() { TODO() }

    // Only valid inside a write transaction
    // Will find the latest version of the object and pass it as an argument
    // so it can be updated
    fun update(action: (obj: E?) -> Unit) { TODO() }

    // QUESTION: Should we let listeners return the Cancellable. Usage pattern would be similar to RxJava?
    fun addChangeListener(listener: RealmChangeListener<E>) { TODO() }
    fun removeChangeListener(listener: RealmChangeListener<E>)  { TODO() }
    fun removeAllListeners() { TODO() }

    // QUESTION: Should we expose both variants?
    fun observe(): Flow<E?> { TODO() }
    fun observeChangesets(): Flow<ObjectChange<E>> { TODO() }
}