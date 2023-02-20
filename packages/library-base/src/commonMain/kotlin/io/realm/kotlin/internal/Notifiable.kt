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
import io.realm.kotlin.internal.util.Validation.sdkError
import kotlinx.coroutines.flow.Flow

/**
 * An _observable_ is an entity that from a user perspective supports some kind of notification
 * mechanism. This does not necessarily mean that you can listen for changes of the object itself,
 * but could be updates of derived entities, i.e. observing a query will actually emit results.
 */
// TODO Public due to being a transitive dependency to Observable
public interface Observable<T : CoreNotifiable<T, C>, C> {

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
 */
public interface Notifiable<T : CoreNotifiable<T, C>, C> {

    /**
     * Should return the live reference in [liveRealm] that the [SuspendableNotifier] will register
     * notifications for with Core, or `null` if the entity has been deleted.
     */
    public fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<T, C>?

    /**
     * The [ChangeBuilder] responsible for converting core changes to appropriate [C]-change events.
     */
    public fun changeBuilder(): ChangeBuilder<T, C>
}

/**
 * A _change builder_ is responsible for converting the core notification change object into an
 * appropriate [C]-change event. If
 */
public abstract class ChangeBuilder<T, C> {

    private var previousElement: T? = null

    /**
     * Converts the given [SuspendableNotifier] event into a C-change event.
     *
     * @param frozenRef a frozen reference of the original entity, or `null` if the entity is no
     * longer present in the [SuspendableNotifier]'s live realm.
     * @param change the core change, or `null` if this is the initial event issued by the
     * [SuspendableNotifier] at the point of callback registration.
     * @return a C-event based on the following rules:
     * - If `frozenRef == null` this method will return the result of calling [delete].
     * - If `frozenRef != null` and `previousElement == null` this method will return the result of
     *   calling [initial].
     * - Otherwise this method will return the result of calling [update].
     */
    internal fun change(frozenRef: T?, change: RealmChangesPointer? = null): C? {
        val event = if (frozenRef != null) {
            if (previousElement == null) {
                initial(frozenRef)
            } else {
                change?.let { update(frozenRef, it) }
                    ?: sdkError("We should never receive change callbacks for non-null (deleted) entities without an actual change object")
            }
        } else {
            delete()
        }
        previousElement = frozenRef
        return event
    }

    internal abstract fun initial(frozenRef: T): C
    internal abstract fun update(frozenRef: T, change: RealmChangesPointer): C?
    internal abstract fun delete(): C
}

// TODO Why is this flowable here?
public interface Flowable<T> {
    public fun asFlow(): Flow<T>
}

/**
 * A _core notifiable_ that supports the various operations on the entity [T] to support
 * registration with the [SuspendableNotifier]. This includes thawing the initial frozen version
 * in the the notifiers live context, register for notifications and freezing the updated version
 * before signaling it to the flow.
 *
 * All [CoreNotifiable] are themselves also [Notifiable] and [Observable].
 */
public interface CoreNotifiable<T, C> : Notifiable<T, C>, Observable<T, C>, Versioned, Flowable<C>
        where T : CoreNotifiable<T, C> {
    public fun thaw(liveRealm: RealmReference): T?
    public fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer
    public fun freeze(frozenRealm: RealmReference): T?

    // Default implementation as all Observables are just thawing themselves.
    override fun notifiable(): Notifiable<T, C> = this
    override fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<T, C>? = thaw(liveRealm.realmReference)
}
