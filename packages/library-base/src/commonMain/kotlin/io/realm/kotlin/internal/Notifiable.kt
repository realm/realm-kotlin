/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.realm.kotlin.internal

import io.realm.kotlin.Versioned
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import kotlinx.coroutines.flow.Flow

public interface ChangeBuilder<T, C> {
    public fun change(frozenRef: T?, change: RealmChangesPointer? = null): Pair<C?, Boolean>
}

public interface Observable<T : CoreObservable<T, C>, C> {
    public fun coreObservable(liveRealm: LiveRealm): CoreObservable<T, C>?
    public fun changeBuilder(): ChangeBuilder<T, C>
}
/**
 * Top level
 */
// TODO Public due to being a transitive dependency to Observable
public interface NotificationFlowable<T : CoreObservable<T, C>, C> {
    public fun observable(): Observable<T, C>
}

// TODO Why is this flowable here?
public interface Flowable<T> {
    public fun asFlow(): Flow<T>
}

public interface CoreObservable<T, C> : Flowable<C>, Observable<T, C>, NotificationFlowable<T, C>, Versioned
        where T : CoreObservable<T, C> {

    // Default implementation as all Observables are just thawing themselves
    override fun observable(): Observable<T, C> = this
    override fun coreObservable(liveRealm: LiveRealm): CoreObservable<T, C>? = thaw(liveRealm.realmReference)

    public fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer
    public fun freeze(frozenRealm: RealmReference): T?
    public fun thaw(liveRealm: RealmReference): T?
}
