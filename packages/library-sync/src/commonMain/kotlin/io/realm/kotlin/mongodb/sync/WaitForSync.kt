/*
 * Copyright 2023 Realm Inc.
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

import io.realm.kotlin.mongodb.ext.subscribe
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults

/**
 * Enum defining the behaviour of when [RealmQuery.subscribe] and [RealmResults.subscribe] will
 * return a query result.
 *
 * When the [Subscription] is created for the first time, data needs to be downloaded from the
 * server before it becomes available, so depending on whether you run the query against the local
 * database before or after this has happened, you query results might not look correct.
 *
 * This enum thus defines the behaviour of when the query is run, so it possible to make the
 * appropriate tradeoff between correctness and availability.
 *
 * @see io.realm.kotlin.mongodb.ext.subscribe
 */
public enum class WaitForSync {
    /**
     * This mode will wait for the server data the first time a subscription is created before
     * running the local query. Later calls to [io.realm.kotlin.mongodb.ext.subscribe] will detect
     * that the subscription already exist and run the query immediately.
     *
     * This is the default mode.
     */
    FIRST_TIME,
    /**
     * With this mode enabled, Realm will always download the latest server state before running
     * the local query. This means that your query result is always seeing the latest data, but
     * it also requires the app to be online.
     */
    ALWAYS,
    /**
     * With this mode enabled, Realm will always query the local database first while any server
     * data is being downloaded in the background. This update is not binary, which means that if
     * you register a flow on the query result, you might see multiple events being emitted as the
     * database is being filled based on the subscription.
     */
    NEVER
}
