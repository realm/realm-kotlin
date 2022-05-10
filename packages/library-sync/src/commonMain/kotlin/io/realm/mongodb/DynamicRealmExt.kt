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

package io.realm.mongodb

import io.realm.dynamic.DynamicRealm
import io.realm.mongodb.internal.SyncedRealmContext
import io.realm.mongodb.internal.executeInSyncContext
import io.realm.mongodb.sync.SubscriptionSet
import io.realm.mongodb.sync.SyncSession

/**
 * Returns the [SyncSession] associated with this Realm.
 */
public val DynamicRealm.syncSession: SyncSession
    get() {
        return executeInSyncContext(this) { context: SyncedRealmContext<DynamicRealm> ->
            context.session
        }
    }

/**
 * Returns the latest [SubscriptionSet] associated with this Realm.
 */
public val DynamicRealm.subscriptions: SubscriptionSet<DynamicRealm>
    get() {
        return executeInSyncContext(this) { context: SyncedRealmContext<DynamicRealm> ->
            // TODO Check for flexible
            context.subscriptions
        }
    }
