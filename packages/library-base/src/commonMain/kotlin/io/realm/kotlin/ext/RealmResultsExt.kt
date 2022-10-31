package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.TypedRealmObject

/**
 * TODO
 */
public inline fun <reified T : TypedRealmObject> RealmResults<T>.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): List<T> {
    return this.getRealm<TypedRealm>()?.copyFromRealm(this, depth, closeAfterCopy)
        ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
