package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.mongodb.types.AsymmetricRealmObject

/**
 * TODO
 */
public fun <T : AsymmetricRealmObject> MutableRealm.insert(instance: T) {
    @Suppress("invisible_member", "invisible_reference")
    if (this is io.realm.kotlin.internal.InternalMutableRealm) {
        io.realm.kotlin.internal.copyToRealm(
            configuration.mediator,
            realmReference,
            instance,
            UpdatePolicy.ERROR
        )
    } else {
        throw IllegalStateException("Calling copyToRealm() on $this is not supported.")
    }
}
