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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.getRealm
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
import kotlin.time.Duration

@Suppress("invisible_reference", "invisible_member")
internal suspend fun <T : RealmObject> createSubscriptionFromQuery(
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
