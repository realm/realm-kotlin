package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.EmbeddedRealmObject

/**
 * TODO Put this in BaseRealmObject and just throw for DynamicRealmObject? Right now we copy this between RealmObject and here
 */
public inline fun <reified T : EmbeddedRealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth, closeAfterCopy)
    } ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
