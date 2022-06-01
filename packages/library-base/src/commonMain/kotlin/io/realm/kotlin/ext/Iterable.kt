package io.realm.kotlin.ext

import io.realm.kotlin.internal.UnmanagedRealmList
import io.realm.kotlin.types.RealmList

/**
 * Instantiates an **unmanaged** [RealmList] containing all the elements of this iterable.
 */
public fun <T> Iterable<T>.toRealmList(): RealmList<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedRealmList()
            1 -> realmListOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedRealmList<T>().apply { addAll(this@toRealmList) }
        }
    }
    return UnmanagedRealmList<T>().apply { addAll(this@toRealmList) }
}
