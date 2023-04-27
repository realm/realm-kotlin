@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmObject
import kotlin.time.Duration

/**
 * TODO Anonymous sub-query on RealmResults
 */
public suspend fun <T : RealmObject> RealmResults<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    val query: RealmQuery<T> = this.query("")
    return query.subscribe(mode, timeout)
}

/**
 * TODO Named sub-query on RealmResults
 */
public suspend fun <T : RealmObject> RealmResults<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    val query: RealmQuery<T> = this.query("")
    return query.subscribe(name, updateExisting, mode, timeout)
}

/**
 * TODO Remove subscription again
 */
public suspend fun <T : RealmObject> RealmResults<T>.unsubscribe(): Boolean {
    return if (this is io.realm.kotlin.internal.RealmResultsImpl) {
        val result: io.realm.kotlin.internal.RealmResultsImpl<*> = this
        var removedSubscription = false
        result.getRealm<Realm>().subscriptions.update {
            find { it.id == result.backingSubscriptionId }?.let {
                remove(it)
                removedSubscription = true
            }
        }
        removedSubscription
    } else {
        false
    }
}

/**
 * TODO Remove a subscription from a query
 */
public suspend fun <T : RealmObject> RealmQuery<T>.unsubscribe(name: String? = null): Boolean {
    return if (this is io.realm.kotlin.internal.query.ObjectQuery) {
        val query = this
        var subscriptionRemoved = false
        val queryDesc = query.description().trim()
        query.getRealm<Realm>().subscriptions.update {
            if (name != null) {
                findByName(name)?.let { sub: Subscription ->
                    if (sub.queryDescription.trim() == queryDesc) {
                        remove(sub)
                        subscriptionRemoved = true
                    }
                }
            } else {
                find {
                    it.name == null && it.queryDescription.trim() == queryDesc
                }?.let {
                    remove(it)
                    subscriptionRemoved
                }
            }
        }
        subscriptionRemoved
    } else {
        false
    }
}
