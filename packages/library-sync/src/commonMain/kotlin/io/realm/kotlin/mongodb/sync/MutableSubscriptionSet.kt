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

package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.RealmObject
import io.realm.kotlin.query.RealmQuery
import kotlin.reflect.KClass

/**
 * A mutable subscription set makes it possible to add, remove or modify a
 * [SubscriptionSet]. It becomes available when calling [SubscriptionSet.update].
 *
 * @see SubscriptionSet for more information about subscription sets and Flexible Sync.
 */
public interface MutableSubscriptionSet : BaseSubscriptionSet {

    /**
     * Adds a new subscription to the subscription set. If an existing subscription exists
     * that matches the [query] and the [name], this operation does nothing and the existing
     * subscription will be returned.
     *
     * If an existing named subscription exists on a different query an [IllegalArgumentException]
     * will be thrown unless [updateExisting] is set to `true`, in which case the existing
     * subscription will be updated with the new query.
     *
     * @param query the query that will be subscribed to. Note, subscription queries have
     * restrictions compared to normal queries.
     * @param name the name of the subscription. If no name is provided, the subscription is
     * considered to be anonymous.
     * @param updateExisting determines the behaviour if an existing named subscription
     * already exists. This does nothing for anonymous subscriptions.
     * @return the newly added subscription.
     * @throws IllegalStateException if a subscription matching the provided one already exists
     * but on a different query and [updateExisting] was set to `false`.
     */
    public fun <T : RealmObject> add(query: RealmQuery<T>, name: String? = null, updateExisting: Boolean = false): Subscription

    /**
     * Creates an anonymous [Subscription] in the current [MutableSubscriptionSet] directly from
     * a [RealmQuery].
     *
     * @return the [Subscription] that was added.
     */
    public fun RealmQuery<out RealmObject>.subscribe(): Subscription = add(this)

    /**
     * Creates a named [Subscription] in the current [MutableSubscriptionSet] directly from a
     * [RealmQuery].
     *
     * @param name name of the subscription.
     * @param updateExisting if a different query is already registered with the provided [name],
     * then set this to `true` to update the subscription. If set to `false` an exception is
     * thrown instead of updating the query.
     * @return the [Subscription] that was added or updated.
     * @throws IllegalArgumentException if [updateExisting] is false, and another query was already
     * registered with the given [name].
     */
    public fun RealmQuery<out RealmObject>.subscribe(name: String, updateExisting: Boolean = false): Subscription =
        add(this, name, updateExisting)

    /**
     * Remove a subscription.
     *
     * @param subscription subscription to remove
     * @return `true` if the subscription was removed, `false` if not.
     */
    public fun remove(subscription: Subscription): Boolean

    /**
     * Remove a named subscription.
     *
     * @param name name of the subscription to remove.
     * @return `true` if a matching subscription was removed, `false` if [Subscription] could be
     * found.
     */
    public fun remove(name: String): Boolean

    /**
     * Remove all subscriptions with queries on a given [Subscription.objectType].
     *
     * @param objectType subscriptions on this object type will be removed.
     * @return `true` if one or more subscriptions were removed, `false` if no
     * subscriptions were removed.
     * @throws IllegalArgumentException if [objectType] is not part of the Schema for this Realm.
     */
    public fun removeAll(objectType: String): Boolean

    /**
     * Remove all subscriptions with queries on a given model class.
     *
     * @param type subscriptions on this type will be removed.
     * @return `true` if one or more subscriptions were removed, `false` if no
     * subscriptions were removed.
     * @throws IllegalArgumentException if [objectType] is not part of the Schema for this Realm.
     */
    public fun <T : RealmObject> removeAll(type: KClass<T>): Boolean

    /**
     * Remove all subscriptions in this subscription set.
     *
     * @return `true` if one or more subscriptions were removed, `false` if the subscription set
     * was empty.
     */
    public fun removeAll(): Boolean
}
