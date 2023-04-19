@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.WaitForSynchronizationMode
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.time.Duration

// Anonymous top-level query
public suspend fun <T: RealmObject> RealmQuery<T>.subscribe(
    mode: WaitForSynchronizationMode = WaitForSynchronizationMode.ON_CREATION,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    val results = (this as io.realm.kotlin.internal.RealmResultsImpl<T>)
    val realm = this.getRealm<Realm>()
    val query: RealmQuery<T> = this
    val subs = realm.subscriptions.update {
        this.add(query)
    }
    // TODO: Need a way to easily get the state of the inserted subscription
    //  How can we differ between inserted or updated?
    val sub = subs.last()

    // TODO What is the expected result here? With frozen objects it gets a bit muddied,
    //  but I suspect we need to refresh the Realm here?
    return this.find()
}

// Named top-level query
public suspend fun <T: RealmObject> RealmQuery<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSynchronizationMode = WaitForSynchronizationMode.ON_CREATION,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    TODO()
}


