package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.mongodb.sync.WaitForSynchronizationMode
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.TypedRealmObject
import kotlin.time.Duration

// Anonymous sub-query on RealmResults
public suspend fun <T: TypedRealmObject> RealmResults<T>.subscribe(
    mode: WaitForSynchronizationMode = WaitForSynchronizationMode.ON_CREATION,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    TODO()
}

// Named sub-query on RealmResults
public suspend fun <T: TypedRealmObject> RealmResults<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSynchronizationMode = WaitForSynchronizationMode.ON_CREATION,
    timeout: Duration = Duration.INFINITE
): RealmResults<T> {
    TODO()
}

// Remove subscriptions again
public suspend fun <T: TypedRealmObject> RealmResults<T>.unsubscribe(): Boolean {
    TODO()
}

