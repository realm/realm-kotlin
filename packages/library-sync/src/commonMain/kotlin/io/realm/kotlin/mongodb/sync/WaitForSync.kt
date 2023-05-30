package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.mongodb.ext.subscribe

/**
 * Enum defining the behaviour of when [RealmQuery.subscribe] and [RealmResults.subscribe] will
 * return a query result.
 *
 * When the [Subscription] is created for the first time, data needs to be downloaded from the
 * server before it becomes available, so depending on whether you run the query against the local
 * database before or after this has happened, you query results might look "wrong".
 *
 * This enum thus defines the behaviour of when the query is run, so it possible to make the
 * appropriate tradeoff between "correctness" and "live-ness".
 */
public enum class WaitForSync {
    /**
     * This mode will wait for the server data the first time a subscription is created before
     * running the local query. Later calls to `subscribe` will detect that the subscription
     * already exist and run the query immediately.
     *
     * This is the default mode.
     */
    FIRST_TIME,
    /**
     * With this mode enabled, Realm will always download the latest server state before running
     * the local query. This means that you query result is always seeing the latest data, but
     * it also require the app to be online.
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
