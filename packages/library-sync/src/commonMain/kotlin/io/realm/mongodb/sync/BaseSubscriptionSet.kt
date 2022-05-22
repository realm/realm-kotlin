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

import io.realm.RealmObject
import io.realm.query.RealmQuery

/**
 * Base interface for shared functionality between [SubscriptionSet] and [MutableSubscriptionSet].
 */
public interface BaseSubscriptionSet : Iterable<Subscription> {

    /**
     * Find the first subscription that contains the given query. It is possible for multiple
     * named subscriptions to contain the same query.
     *
     * @param query query to search for.
     * @return the first subscription containing the query or `null` if no match was found.
     */
    public fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription?

    /**
     * Find the subscription with a given name.
     *
     * @param name name of subscription to search for.
     * @return the matching subscription or `null` if no subscription with that name was found.
     */
    public fun findByName(name: String): Subscription?

    /**
     * The current state of the SubscriptionSet. See [SubscriptionSetState] for more
     * details about each state.
     */
    public val state: SubscriptionSetState

    /**
     * If [state] returns [SubscriptionSetState.ERROR], this method will return the reason.
     * Errors can be fixed by modifying the subscription accordingly and then call
     * [SubscriptionSet.waitForSynchronization].
     *
     * @return the underlying error if the subscription set is in the [SubscriptionSetState.ERROR]
     * state. For all other states `null` will be returned.
     */
    public val errorMessage: String?

    /**
     * The number of subscriptions currently in this subscription set.
     */
    public val size: Int
}
