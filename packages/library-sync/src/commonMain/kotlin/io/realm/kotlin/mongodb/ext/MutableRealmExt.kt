package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.mongodb.types.AsymmetricRealmObject

/**
 * Insert a [AsymmetricRealmObject] into Realm. Since asymmetric objects are "write-only", it is
 * not possible to access the managed data after it has been inserted.
 *
 * @param obj the object to create a copy from.
 * @throws IllegalArgumentException if the object graph of [obj] either contains an object
 * with a primary key value that already exists, if the object graph contains an object from a
 * previous version or if a property does not match the underlying schema.
 */
public fun <T : AsymmetricRealmObject> MutableRealm.insert(obj: T) {
    @Suppress("invisible_member", "invisible_reference")
    if (this is io.realm.kotlin.internal.InternalMutableRealm) {
        io.realm.kotlin.internal.copyToRealm(
            configuration.mediator,
            realmReference,
            obj,
            UpdatePolicy.ERROR
        )
    } else {
        throw IllegalStateException("Calling insert() on $this is not supported.")
    }
}
