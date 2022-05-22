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

package io.realm.mongodb.sync

import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.query.RealmQuery
import kotlin.reflect.KClass

/**
 * A subscription defines a specific server query and its metadata. The result of this query
 * is continuously being synchronized with the device as long as the subscription is part of a
 * [SubscriptionSet] with a state of [SubscriptionSetState.COMPLETE].
 *
 * Subscriptions can be updated using [MutableSubscriptionSet.add] with `updateExisting = true`.
 */
public interface Subscription {

    /**
     * The timestamp for when this subscription was created.
     */
    public val createdAt: RealmInstant

    /**
     * The timestamp for when a persisted subscription was updated. When the subscription
     * is created, this field is equal to [createdAt].
     */
    public val updatedAt: RealmInstant

    /**
     * The name of subscription or `null` if this is an anonymous subscription.
     */
    public val name: String?

    /**
     * The class name of the objects being queried.
     */
    public val objectType: String

    /**
     * The subscription query that is running on objects of type [objectType].
     */
    public val queryDescription: String

    /**
     * Converts the [Subscription.queryDescription] back to a [RealmQuery] that can be run against
     * the current state of the local Realm.
     *
     * @param type a reference to the Kotlin model class that represents [objectType].
     * @throws IllegalArgumentException if [type] does not match the type of objects this query
     * can return.
     */
    public fun <T : RealmObject> asQuery(type: KClass<T>): RealmQuery<T>
}

/**
 * Converts the [Subscription.queryDescription] back to a [RealmQuery] that can be run against
 * the current state of the local Realm.
 *
 * @param T a reference to the Kotlin model class that represents the [Subscription.objectType].
 * @throws IllegalArgumentException if [type] does not match the type of objects this query
 * can return.
 */
public inline fun <reified T : RealmObject> Subscription.asQuery(): RealmQuery<T> =
    asQuery(T::class)
