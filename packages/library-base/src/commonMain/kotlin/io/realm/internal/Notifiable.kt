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

internal interface Notifiable<T> {
    fun registerForNotification(callback: Callback): NativePointer

    // FIXME Needs elaborate doc on how to signal and close channel
    fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<T>
    ): ChannelResult<Unit>?
}

internal interface Freezable<T> {
    fun freeze(frozenRealm: RealmReference): Notifiable<T>?
}

internal interface Thawable<T> {
    fun thaw(liveRealm: RealmReference): Notifiable<T>?
}

// TODO Why is this flowable here?
public interface Flowable<T> {
    public fun asFlow(): Flow<T?>
}

internal interface Observable<T> : Notifiable<T>, Freezable<T>, Thawable<T>
