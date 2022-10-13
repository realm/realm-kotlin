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
package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.BaseRealm
import io.realm.kotlin.internal.BaseRealmImpl
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession

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
    internal val config: SyncConfiguration = baseRealm.configuration as SyncConfiguration
    // Note: Session and Subscriptions only need a valid dbPointer when being created, after that, they
    // have their own lifecycle and can be cached.
    internal val session: SyncSession by lazy {
        SyncSessionImpl(
            baseRealm,
            RealmInterop.realm_sync_session_get(baseRealm.realmReference.dbPointer)
        )
    }
    internal val subscriptions: SubscriptionSet<T> by lazy {
        SubscriptionSetImpl(
            realm,
            RealmInterop.realm_sync_get_latest_subscriptionset(baseRealm.realmReference.dbPointer)
        )
    }
}

/**
 * Helper methods that can be used by public API entry points to grant safe access to the
 * [SyncedRealmContext], or otherwise throw an appropriate exception.
 */
internal fun <T, R : BaseRealm> executeInSyncContext(realm: R, block: (context: SyncedRealmContext<R>) -> T): T {
    if (realm.isClosed()) {
        throw IllegalStateException("This method is not available when the Realm has been closed.")
    }
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
    val syncContext = (realm as RealmImpl).syncContext
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
