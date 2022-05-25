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
import io.realm.RealmObject
import io.realm.internal.BaseRealmImpl
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.mongodb.sync.BaseSubscriptionSet
import io.realm.mongodb.sync.Subscription
import io.realm.mongodb.sync.SubscriptionSetState
import io.realm.query.RealmQuery

internal abstract class BaseSubscriptionSetImpl<T : BaseRealm>(
    protected val realm: T,
) : BaseSubscriptionSet {

    protected abstract val nativePointer: RealmBaseSubscriptionSetPointer

    protected abstract fun getIteratorSafePointer(): RealmBaseSubscriptionSetPointer

    protected fun checkClosed() {
        (realm as BaseRealmImpl).realmReference.checkClosed()
    }

    @Suppress("invisible_reference", "invisible_member")
    override fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription? {
        val queryPointer = (query as io.realm.internal.query.ObjectQuery).queryPointer
        return nativePointer.let { subscriptionSetPointer: RealmBaseSubscriptionSetPointer ->
            val subscriptionPointer: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_query(
                subscriptionSetPointer,
                queryPointer
            )
            if (subscriptionPointer == null)
                null
            else
                SubscriptionImpl(realm, subscriptionSetPointer, subscriptionPointer)
        }
    }

    override fun findByName(name: String): Subscription? {
        val sub: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_name(
            nativePointer,
            name
        )
        return if (sub == null) null else SubscriptionImpl(realm, nativePointer, sub)
    }

    override val state: SubscriptionSetState
        get() {
            val state = RealmInterop.realm_sync_subscriptionset_state(nativePointer)
            return stateFrom(state)
        }

    override val errorMessage: String?
        get() = RealmInterop.realm_sync_subscriptionset_error_str(nativePointer)

    override val size: Int
        get() = RealmInterop.realm_sync_subscriptionset_size(nativePointer).toInt()

    override fun iterator(): Iterator<Subscription> {
        // We want to keep iteration stable even if a SubscriptionSet is refreshed
        // during iteration. In order to do so, the iterator needs to own the pointer.
        // But since here doesn't seem to be a way to clone a subscription set at a
        // given version we use the latest version instead.
        //
        // This means there is small chance the set of subscriptions is different
        // than the one you called `iterator` on, but since that point to a race
        // condition in the users logic, we accept it.
        //
        // For MutableSubscriptionSets, we just re-use the pointer as there is no
        // API to refresh the set. It is still possible to get odd results if you
        // add subscriptions during iteration, but this is no different than any
        // other iterator.
        val iteratorPointer = getIteratorSafePointer()

        return object : Iterator<Subscription> {
            private val nativePointer: RealmBaseSubscriptionSetPointer = iteratorPointer
            private var cursor = 0L
            private val size: Long = RealmInterop.realm_sync_subscriptionset_size(nativePointer)

            override fun hasNext(): Boolean {
                return cursor < size
            }

            override fun next(): Subscription {
                if (cursor >= size) {
                    throw NoSuchElementException(
                        "Iterator has no more elements. " +
                            "Tried index " + cursor + ". Size is " + size + "."
                    )
                }
                val ptr = RealmInterop.realm_sync_subscription_at(nativePointer, cursor)
                cursor++
                return SubscriptionImpl(realm, nativePointer, ptr)
            }
        }
    }

    internal companion object {
        internal fun stateFrom(coreState: CoreSubscriptionSetState): SubscriptionSetState {
            return when (coreState) {
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_UNCOMMITTED ->
                    SubscriptionSetState.UNCOMMITTED
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_PENDING ->
                    SubscriptionSetState.PENDING
                CoreSubscriptionSetState.RLM_SYNC_BOOTSTRAPPING ->
                    SubscriptionSetState.BOOTSTRAPPING
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE ->
                    SubscriptionSetState.COMPLETE
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_ERROR ->
                    SubscriptionSetState.ERROR
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_SUPERSEDED ->
                    SubscriptionSetState.SUPERCEDED
                else -> TODO("Unsupported state: $coreState")
            }
        }
    }
}
