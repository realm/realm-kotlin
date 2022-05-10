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
package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.internal.BaseRealmImpl
import io.realm.internal.RealmImpl
import io.realm.internal.interop.RealmInterop
import io.realm.mongodb.sync.SubscriptionSet
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession

/**
 * Since extension functions has limited capabilities, like not allowing backing fields. This class
 * contains all fields and functionality needed to expose public Sync API's. Meaning that the
 * extension functions only need to verify that the call is valid and otherwise just delegate
 * to this class.
 *
 * In order to work around the bootstrap problem, all public API entry points that access this
 * class must do so through the [executeInSyncContext] closure.
 */
internal class SyncedRealmContext<T : BaseRealm>(realm: T) {
    // TODO For now this can only be a RealmImpl, which is required by the SyncSessionImpl
    //  When we introduce a public DynamicRealm, this can also be a `DynamicRealmImpl`
    //  And we probably need to modify the SyncSessionImpl to take either of these two.
    private val baseRealm = realm as RealmImpl
    private val dbPointer = baseRealm.realmReference.dbPointer
    internal val session: SyncSession =
        SyncSessionImpl(baseRealm, RealmInterop.realm_sync_session_get(dbPointer))
    internal val subscriptions: SubscriptionSet<T> =
        SubscriptionSetImpl<T>(realm, RealmInterop.realm_sync_get_latest_subscriptionset(dbPointer))
}

/**
 * Helper methods that can be used by public API entry points to grant safe access to the
 * [SyncedRealmContext], or otherwise throw an appropriate exception.
 */
internal fun <T, R : BaseRealm> executeInSyncContext(realm: R, block: (context: SyncedRealmContext<R>) -> T): T {
    val config = realm.configuration
    if (config is SyncConfiguration) {
        if (realm is BaseRealmImpl) {
            val context: SyncedRealmContext<R> = initSyncContextIfNeeded(realm)
            return block(context)
        } else {
            // Should never happen. Indicates a problem with our internal architecture.
            throw IllegalStateException("This method is not available on objects of type: $realm")
        }
    } else {
        // Public error
        throw IllegalStateException("This method is only available on synchronized realms.")
    }
}

private fun <T : BaseRealm> initSyncContextIfNeeded(realm: T): SyncedRealmContext<T> {
    // INVARIANT: `syncContext` is only ever set once, and never to `null`.
    // This code works around the fact that `Mutex`'s can only be locked inside suspend functions on
    // Kotlin Native.
    val syncContext = (realm as BaseRealmImpl).syncContext
    return if (syncContext.value != null) {
        syncContext.value!! as SyncedRealmContext<T>
    } else {
        // Worst case, two SyncedRealmContext will be created and one of them will thrown
        // away. As long as SyncedRealmContext is cheap to create, this should be fine. If, at
        // some point, it start having too much state, we can consider making `lazy` properties
        // inside the class to defer the construction cost.
        syncContext.compareAndSet(null, SyncedRealmContext<T>(realm))
        syncContext.value!! as SyncedRealmContext<T>
    }
}
