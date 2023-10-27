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
import io.realm.kotlin.internal.interop.RealmKeyPathArray
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.internal.util.trySendWithBufferOverflowCheck
import kotlinx.coroutines.channels.ProducerScope

/**
 * An _observable_ is an entity that from a user perspective supports some kind of notification
 * mechanism. This does not necessarily mean that you can listen for changes of the object itself,
 * but could be updates of derived entities, i.e. observing a query will actually emit results.
 *
 * @param T the type of entity that is observed.
 * @param C the type of change events emitted for the T entity.
 */
internal interface Observable<T : CoreNotifiable<T, C>, C> {

    /**
     * Returns the [Notifiable] describing how the [SuspendableNotifier] should register and emit
     * change events for the given [Observable].
     */
    public fun notifiable(): Notifiable<T, C>
}

/**
 * A _notifiable_ yields the live reference and change event builder that is used by the
 * [SuspendableNotifier] to register for notifications with core and convert the core change sets
 * into [C]-change events.
 *
 * @param T the type of entity that is observed.
 * @param C the type of change events emitted for the T entity.
 */
internal interface Notifiable<T : CoreNotifiable<T, C>, C> {

    /**
     * Should return the live reference in [liveRealm] that the [SuspendableNotifier] will register
     * notifications for with Core, or `null` if the entity has been deleted.
     */
    public fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<T, C>?

    /**
     * The [ChangeFlow] responsible for emitting [SuspendableNotifier] events as appropriate
     * [C]-change events.
     */
    public fun changeFlow(scope: ProducerScope<C>): ChangeFlow<T, C>
}

/**
 * A _change flow_ is responsible for converting the [SuspendableNotifier] events into
 * corresponding [C]-change events and emit them in the [producerScope] and close the scope when
 * the monitored entity is deleted.

 * @param T the type of entity that is observed.
 * @param C the type of change events emitted for the T entity.
 */
// TODO Public due to being a transitive dependency from RealmObjectReference
public abstract class ChangeFlow<T, C>(private val producerScope: ProducerScope<C>) {

    private var initialElement: Boolean = true

    /**
     * Converts the given [SuspendableNotifier] event into a C-change event, emit it and potentially
     * close the [producerScope] if the monitored event was deleted (`frozenRef == null`).
     *
     * The default implementation will emit a C-event based on the following rules:
     * - If `frozenRef == null` the result of calling [delete] will be emitted and the
     *   [producerScope] will be closed.
     * - If `frozenRef != null` and `previousElement == null` the result of calling [initial] will
     *   be emitted.
     * - Otherwise the result of calling [update] will be emitted.
     *
     * @param frozenRef a frozen reference of the original entity, or `null` if the entity is no
     * longer present in the [SuspendableNotifier]'s live realm.
     * @param change the core change, or `null` if this is the initial event issued by the
     * [SuspendableNotifier] at the point of callback registration.
     */
    internal fun emit(frozenRef: T?, change: RealmChangesPointer? = null) {
        val event = if (frozenRef != null) {
            if (initialElement) {
                initialElement = false
                initial(frozenRef)
            } else {
                change?.let { update(frozenRef, it) }
                    ?: sdkError("We should never receive change callbacks for non-null (deleted) entities without an actual change object")
            }
        } else {
            delete()
        }
        event?.let { producerScope.trySendWithBufferOverflowCheck(it) }
        if (frozenRef == null) {
            producerScope.close()
        }
    }

    internal abstract fun initial(frozenRef: T): C
    internal abstract fun update(frozenRef: T, change: RealmChangesPointer): C?
    internal abstract fun delete(): C
}

/**
 * A _core notifiable_ that supports the various operations on the entity [T] to support
 * registration with the [SuspendableNotifier]. This includes thawing the initial frozen version
 * in the the notifiers live context, register for notifications and freezing the updated version
 * before signaling it to the flow.
 *
 * All [CoreNotifiable] are themselves also [Notifiable] and [Observable].

 * @param T the type of entity that is observed.
 * @param C the type of change events emitted for the T entity.
 */
internal interface CoreNotifiable<T, C> : Notifiable<T, C>, Observable<T, C>, Versioned, KeyPathFlowable<C>
        where T : CoreNotifiable<T, C> {
    public fun thaw(liveRealm: RealmReference): T?
    public fun registerForNotification(keyPaths: RealmKeyPathArray?, callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer
    public fun freeze(frozenRealm: RealmReference): T?

    // Default implementation as all Observables are just thawing themselves.
    override fun notifiable(): Notifiable<T, C> = this
    override fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<T, C>? = thaw(liveRealm.realmReference)
}
