@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.mongodb.annotations.ExperimentalFlexibleSyncApi
import io.realm.kotlin.mongodb.internal.AppImpl
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * Automatically create an anonymous [Subscription] from a query in the background and return the
 * result of running the same query against the local Realm file.
 *
 * This is a more streamlined alternative to doing something like this:
 *
 * ```
 * fun suspend getData(realm: Realm): RealmResults<Person> {
 *     realm.subscriptions.update { bgRealm ->
 *         add(bgRealm.query<Person>())
 *     }
 *     realm.subscriptions.waitForSynchronization()
 *     return realm.query<Person>().find()
 * }
 * ```
 *
 * It is possible to define whether or not to wait for the server to send all data before
 * running the local query. This is relevant as there might be delay from creating a subscription
 * to the data being available on the device due to either latency or because a large dataset needs
 * be downloaded.
 *
 * The default behaviour is that the first time `subscribe` is called, the query result will not
 * be returned until data has been downloaded from the server. On subsequent calls to `subscribe`
 * for the same query, the query will run immediately on the local database while any updates
 * are downloaded in the background.
 *
 * @param mode type of mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Realm file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @Throws IllegalStateException if this method is called on a Realm that isn't using Flexible Sync.
 */
@ExperimentalFlexibleSyncApi
public suspend fun <T : RealmObject> RealmQuery<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    return createSubscriptionFromQuery(this, null, false, mode, timeout)
}

/**
 * Automatically create a named [Subscription] from a query in the background and return the
 * result of running the same query against the local Realm file.
 *
 * This is a more streamlined alternative to doing something like this:
 *
 * ```
 * fun suspend getData(realm: Realm): RealmResults<Person> {
 *     realm.subscriptions.update { bgRealm ->
 *         add("myquery", bgRealm.query<Person>())
 *     }
 *     realm.subscriptions.waitForSynchronization()
 *     return realm.query<Person>().find()
 * }
 * ```
 *
 * It is possible to define whether or not to wait for the server to send all data before
 * running the local query. This is relevant as there might be delay from creating a subscription
 * to the data being available on the device due to either latency or because a large dataset needs
 * be downloaded.
 *
 * The default behaviour is that the first time `subscribe` is called, the query result will not
 * be returned until data has been downloaded from the server. On subsequent calls to `subscribe`
 * for the same query, the query will run immediately on the local database while any updates
 * are downloaded in the background.
 *
 * @param name name of the subscription. This can be used to identify it later in the [SubscriptionSet].
 * @param mode type of mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Realm file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @Throws IllegalStateException if this method is called on a Realm that isn't using Flexible Sync.
 */
@ExperimentalFlexibleSyncApi
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
                if (name != null) subscriptions.findByName(name) else subscriptions.findByQuery(query)
            if (existingSubscription == null || updateExisting) {
                subscriptions.update {
                    add(query, name, updateExisting)
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
            realm.query(query.clazz, query.description()).find()
        }
    }
}
