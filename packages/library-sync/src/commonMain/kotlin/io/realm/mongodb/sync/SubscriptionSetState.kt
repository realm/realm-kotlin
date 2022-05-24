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

/**
 * The possible states a [SubscriptionSet] or [MutableSubscriptionSet] can be in.
 */
public enum class SubscriptionSetState {
    /**
     * The initial state of subscriptions when opening a new Realm or when entering
     * [SubscriptionSet.update].
     */
    UNCOMMITTED,

    /**
     * A subscription set has been modified locally, but is still waiting to be sent to the
     * server.
     */
    PENDING,

    /**
     * A subscription set was accepted by the server and initial data is being sent to the
     * device.
     */
    BOOTSTRAPPING,

    /**
     * The subscription set is valid and active. Any new data will be synchronized in both directions
     * between the server and the device.
     */
    COMPLETE,

    /**
     * An error occurred in the subscription set or one of the subscriptions. The cause is
     * found in [BaseSubscriptionSet.errorMessage].
     */
    ERROR,

    /**
     * Another subscription set was stored before this one, the changes made to this set
     * are ignored by the server. Get the latest subscription set by calling
     * [SubscriptionSet.refresh].
     */
    SUPERCEDED;
}
