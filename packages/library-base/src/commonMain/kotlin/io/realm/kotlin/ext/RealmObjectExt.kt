package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.types.RealmObject

/**
 * TODO Put this in BaseRealmObject and just throw for DynamicRealmObject? Right now we copy this between EmbeddedRealmObject and here
 */
public inline fun <reified T : RealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    // TODO Better type checks
    val obj = this as RealmObjectInternal
    val realm = obj.io_realm_kotlin_objectReference!!.owner.owner as TypedRealm
    return realm.copyFromRealm(obj, depth, closeAfterCopy) as T
}
