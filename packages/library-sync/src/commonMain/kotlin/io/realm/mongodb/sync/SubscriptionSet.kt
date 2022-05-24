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

import io.realm.BaseRealm
import io.realm.mongodb.exceptions.FlexibleSyncQueryException
import io.realm.query.RealmQuery
import kotlin.time.Duration

/**
 * A subscription set is an immutable view of all current [Subscription]s for a given
 * Realm that has been configured for Flexible Sync.
 *
 * Flexible Sync is a way of defining which data gets synchronized to and from the device using
 * [RealmQuery]s. The query and its metadata are represented by a [Subscription].
 *
 * A subscription set thus defines all the data that is available to the device and being
 * synchronized with the server. If the subscription set encounters an error, e.g. by containing an
 * invalid query, the entire subscription set will enter an [SubscriptionSetState.ERROR]
 * state, and no synchronization will happen until the error has been fixed.
 *
 * If a subscription is removed, so is the corresponding data, but it is only removed from the
 * device. It isn't deleted on the server.
 *
 * It is possible to modify a subscription set while offline, but a modification isn't
 * accepted by the server before [BaseSubscriptionSet.state] returns
 * [SubscriptionSetState.COMPLETE], which requires that the device has been online.
 *
 * It is possible to wait for the subscription set to be synchronized with the server by using
 * [waitForSynchronization].
 */
public interface SubscriptionSet<T : BaseRealm> : BaseSubscriptionSet {

    /**
     * Modify the subscription set. If an exception is thrown during the update, no changes will be
     * applied. If the update succeeds, this subscription set is updated with the modified state.
     *
     * @param block the block that modifies the subscription set.
     * @return this subscription set, that now has been updated.
     */
    public suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T>

    /**
     * Wait for the subscription set to synchronize with the server. It will return when the
     * server either accepts the set of queries and has downloaded data for them, or if an
     * error has occurred.
     *
     * @param timeout how long to wait for the synchronization to either succeed or fail.
     * @return `true` if all current subscriptions were accepted by the server and data has
     * been downloaded, or `false` if the [timeout] was hit before all data could be downloaded.
     * @throws FlexibleSyncQueryException if the server did not accept the set of queries. The
     * exact reason is found in the exception message. The [SubscriptionSet] will also enter a
     * [SubscriptionSetState.ERROR] state.
     */
    public suspend fun waitForSynchronization(timeout: Duration = Duration.INFINITE): Boolean

    /**
     * Refresh the [state] of the subscription set, so it reflect the latest underlying state of
     * the subscriptions.
     */
    public fun refresh(): SubscriptionSet<T>
}
