/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.mongodb

import io.realm.kotlin.Realm
import io.realm.kotlin.mongodb.internal.SyncedRealmContext
import io.realm.kotlin.mongodb.internal.executeInSyncContext
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession

/**
 * This class contains extension methods that are available when using synced realms.
 *
 * Calling these methods on a local realms created using a [io.realm.RealmConfiguration] will
 * throw an [IllegalStateException].
 */

/**
 * Returns the [SyncSession] associated with this Realm.
 */
public val Realm.syncSession: SyncSession
    get() {
        return executeInSyncContext(this) { context: SyncedRealmContext<Realm> ->
            context.session
        }
    }

/**
 * Returns the latest [SubscriptionSet] associated with this Realm.
 */
public val Realm.subscriptions: SubscriptionSet<Realm>
    get() {
        return executeInSyncContext(this) { context: SyncedRealmContext<Realm> ->
            if (context.config.syncMode != SyncMode.FLEXIBLE) {
                throw IllegalStateException(
                    "Subscriptions are only available on Realms configured " +
                        "for Flexible Sync. This Realm was configured for Partion-based Sync: " +
                        "${context.config.path}"
                )
            }
            context.subscriptions
        }
    }
