@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * TODO Create anonymous query
 */
public suspend fun <T: RealmObject> RealmQuery<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    return createSubscriptionFromQuery(this, null, false, mode, timeout)
}

/**
 * TODO Create named query
 */
public suspend fun <T: RealmObject> RealmQuery<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    return createSubscriptionFromQuery(this, name, updateExisting, mode, timeout)
}

private suspend fun <T: RealmObject> createSubscriptionFromQuery(
    query: RealmQuery<T>,
    name: String?,
    updateExisting: Boolean = false,
    mode: WaitForSync,
    timeout: Duration
): RealmResults<T> {
    // TODO Use the AppDispatcherFactory so we share threads with the NetworkTransport
    return withContext(Dispatchers.Default) {
        val objectQuery = (query as io.realm.kotlin.internal.query.ObjectQuery<T>)
        val realm = objectQuery.getRealm<Realm>()
        val subscriptions = realm.subscriptions
        val existingSubscription = if (name != null) subscriptions.findByName(name) else subscriptions.findByQuery(query)
        var subscriptionId = existingSubscription?.id
        if (existingSubscription == null || updateExisting) {
            subscriptions.update {
                subscriptionId = this.add(query, name, updateExisting).id
            }
        }
        if (
            (mode == WaitForSync.FIRST_TIME && existingSubscription == null) ||
            (mode == WaitForSync.ALWAYS)
        ) {
            subscriptions.waitForSynchronization(timeout)
        }
        // Rerun the query on the latest Realm version.
        realm.query(objectQuery.clazz, objectQuery.description()).find().also {
            (it as io.realm.kotlin.internal.RealmResultsImpl).backingSubscriptionId = subscriptionId
        }
    }
}



