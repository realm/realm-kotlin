@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.mongodb.internal.AppImpl
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mongodb.kbson.ObjectId
import kotlin.time.Duration

/**
 * TODO Create anonymous query
 */
public suspend fun <T : RealmObject> RealmQuery<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    return createSubscriptionFromQuery(this, null, false, mode, timeout)
}

/**
 * TODO Create named query
 */
public suspend fun <T : RealmObject> RealmQuery<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    return createSubscriptionFromQuery(this, name, updateExisting, mode, timeout)
}

private suspend fun <T : RealmObject> createSubscriptionFromQuery(
    query: RealmQuery<T>,
    name: String?,
    updateExisting: Boolean = false,
    mode: WaitForSync,
    timeout: Duration
): RealmResults<T> {

    if (query !is io.realm.kotlin.internal.query.ObjectQuery<T>) {
        throw IllegalStateException("Only queries on objects are supported. This was: ${query::class}")
    }
    if (query.realmReference.owner !is RealmImpl) {
        throw IllegalStateException("Calling `subscribe()` inside a write transaction is not allowed.")
    }
    val realm: Realm = query.getRealm()
    val subscriptions = realm.subscriptions
    val appDispatcher: CoroutineDispatcher = ((realm.configuration as SyncConfiguration).user.app as AppImpl).appNetworkDispatcher.dispatcher

    return withTimeout(timeout) {
        withContext(appDispatcher) {
            val existingSubscription: Subscription? =
                // FIXME Check that findByQuery actually works
                if (name != null) subscriptions.findByName(name) else subscriptions.findByQuery(query)
            var subscriptionId: ObjectId? = existingSubscription?.id
            if (existingSubscription == null || updateExisting) {
                subscriptions.update {
                    subscriptionId = this.add(query, name, updateExisting).id
                }
            }
            if ((mode == WaitForSync.FIRST_TIME || mode == WaitForSync.ALWAYS) && existingSubscription == null) {
                subscriptions.waitForSynchronization()
            } else if (mode == WaitForSync.ALWAYS) {
                // The subscription should already exist, just make sure we downloaded all
                // server data before continuing.
                realm.syncSession.downloadAllServerChanges()
            }
            // Rerun the query on the latest Realm version.
            realm.query(query.clazz, query.description()).find().also {
                (it as io.realm.kotlin.internal.RealmResultsImpl).backingSubscriptionId = subscriptionId
            }
        }
    }
}
