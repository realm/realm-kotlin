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

package io.realm.internal

import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow

interface Observable<T> {
    fun registerForNotification(callback: Callback): NativePointer

    // FIXME Needs elaborate doc on how to signal and close channel
    fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<T>
    ): ChannelResult<Unit>?
}

/**
 * TODO : query
 */
interface Freezable<T> {
    fun freeze(frozenRealm: RealmReference): Observable<T>?
}

/**
 * TODO : query
 */
interface Thawable<T> {
    fun thaw(liveRealm: RealmReference): Observable<T>?
}

/**
 * TODO : query
 */
interface Flowable<T> {
    fun asFlow(): Flow<T?>
}
