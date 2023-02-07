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
import io.realm.kotlin.internal.util.trySendWithBufferOverflowCheck
import io.realm.kotlin.notifications.internal.Cancellable
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow

public abstract class NotificationFlow<T : Observable<T, C>, C>(
    realm: LiveRealm,
    observable: Observable<T, C>,
    private val channel: ProducerScope<C>
) : Cancellable {

    private val token: AtomicRef<Cancellable> =
        kotlinx.atomicfu.atomic(Cancellable.NO_OP_NOTIFICATION_TOKEN)

    init {
        println("observing: $observable ${observable.version()}")
        val lifeRef: Observable<T, C>? = observable.thaw(realm.realmReference)
        println("observing initial: ${lifeRef?.version()}")
        if (lifeRef != null) {
            val interopCallback: Callback<RealmChangesPointer> =
                object : Callback<RealmChangesPointer> {
                    override fun onChange(change: RealmChangesPointer) {
                        // FIXME How to make sure the Realm isn't closed when handling this?
                        // Notifications need to be delivered with the version they where created on, otherwise
                        // the fine-grained notification data might be out of sync.
                        println("updating initial: ${lifeRef?.version()}")
                        val frozenObservable = lifeRef.freeze(realm.snapshot)
                        println("updating : ${frozenObservable?.version()} ${realm.snapshot}")
                        emit(frozenObservable, change)
                    }
                }
            val newToken =
                NotificationToken(
                    token = lifeRef.registerForNotification(interopCallback)
                )
            token.value = newToken
        }
        // Initial event
        emit(lifeRef?.freeze(realm.snapshot))
    }

    public fun emit(frozenRef: T?, change: RealmChangesPointer? = null) {
        if (frozenRef != null) {
            if (change == null) {
                channel.trySendWithBufferOverflowCheck(initial(frozenRef))
            } else {
                update(frozenRef, change)?.let {
                    channel.trySendWithBufferOverflowCheck(it)
                }
            }
        } else {
            channel.trySendWithBufferOverflowCheck(delete())
            channel.close()
        }
    }
    internal abstract fun initial(frozenRef: T): C
    internal abstract fun update(frozenRef: T, change: RealmChangesPointer): C?
    internal abstract fun delete(): C

    override fun cancel() {
        token.value.cancel()
    }
}

// public interface X {
//    public fun
// }
// TODO Public due to being a transitive dependency to Observable
public interface NotificationFlowable<T : Observable<T, C>, C> {
    public fun observable(liveRealm: LiveRealm, channel: ProducerScope<C>): NotificationFlow<T, C>
}

public interface NativeObservable {
    public fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer

    // FIXME Needs elaborate doc on how to signal and close channel
//    public fun changeEvent(
//        frozenElement: T?,
//        frozenRealm: RealmReference,
//        change: RealmChangesPointer,
//        channel: SendChannel<T>
//    ): Pair<C, Boolean>
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

public interface Observable<T, C> : NativeObservable, Freezable<T>, Thawable<T>, Flowable<C>, NotificationFlowable<T, C>, Versioned
        where T : Observable<T, C>,
              T : Thawable<T>,
              T : Freezable<T>
