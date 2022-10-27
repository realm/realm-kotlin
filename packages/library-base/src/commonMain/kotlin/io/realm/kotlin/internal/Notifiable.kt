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

import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

// TODO Public due to being a transitive dependency to Observable
public interface Notifiable<T> {
    public fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer

    // FIXME Needs elaborate doc on how to signal and close channel
    public fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<T>
    ): ChannelResult<Unit>?
}

// TODO Public due to being a transitive dependency to Observable
public interface Freezable<T> {
    public fun freeze(frozenRealm: RealmReference): T?
}

// TODO Public due to being a transitive dependency to Observable
public interface Thawable<T> {
    public fun thaw(liveRealm: RealmReference): T?
}

// TODO Why is this flowable here?
public interface Flowable<T> {
    public fun asFlow(): Flow<T>
}

public interface Observable<T, C> : Notifiable<C>, Freezable<Observable<T, C>>, Thawable<Observable<T, C>>
