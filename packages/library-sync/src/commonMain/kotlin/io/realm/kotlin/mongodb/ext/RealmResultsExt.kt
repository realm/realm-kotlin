/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("invisible_reference", "invisible_member")

package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.mongodb.annotations.ExperimentalFlexibleSyncApi
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.TRUE_PREDICATE
import io.realm.kotlin.types.RealmObject
import kotlin.time.Duration

/**
 * Automatically create a named [Subscription] from a local query result in the background and
 * return the result of re-running the same query against the Realm file.
 *
 * This is a more streamlined alternative to doing something like this:
 *
 * ```
 * fun suspend getData(realm: Realm): RealmResults<Person> {
 *     val results = realm.query<Person>().find()
 *     realm.subscriptions.update { bgRealm ->
 *         add("myquery", results.query(""))
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
 * The default behaviour is that the first time [subscribe] is called, the query result will not
 * be returned until data has been downloaded from the server. On subsequent calls to [subscribe]
 * for the same query, the query will run immediately on the local database while any updates
 * are downloaded in the background.
 *
 * @param name name of the subscription. This can be used to identify it later in the [SubscriptionSet].
 * @param mode mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Realm file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @throws IllegalStateException if this method is called on a Realm that isn't using Flexible Sync.
 * @throws io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException if the server did not
 * accept the set of queries. The exact reason is found in the exception message.
 */
@ExperimentalFlexibleSyncApi
public suspend fun <T : RealmObject> RealmResults<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    val query: RealmQuery<T> = this.query(TRUE_PREDICATE)
    return query.subscribe(name, updateExisting, mode, timeout)
}

/**
 * Automatically create an anonymous [Subscription] from a local query result in the background and
 * return the result of re-running the same query against the Realm file. This behaves the same
 * as creating a named variant by calling [subscribe]. See this method for details about the
 * exact behavior.
 *
 * @param mode mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Realm file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @Throws IllegalStateException if this method is called on a Realm that isn't using Flexible Sync.
 */
@ExperimentalFlexibleSyncApi
public suspend fun <T : RealmObject> RealmResults<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    val query: RealmQuery<T> = this.query(TRUE_PREDICATE)
    return query.subscribe(mode, timeout)
}
