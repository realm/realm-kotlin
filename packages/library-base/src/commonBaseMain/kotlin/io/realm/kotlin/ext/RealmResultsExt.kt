package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.TypedRealmObject

/**
 * Makes an unmanaged in-memory copy of the elements in a [RealmResults]. This is a deep copy
 * that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <reified T : TypedRealmObject> RealmResults<T>.copyFromRealm(depth: UInt = UInt.MAX_VALUE): List<T> {
    // We don't have unmanaged RealmResults in the API and `getRealm` will throw an exception if
    // the Realm is closed, so all error handling is done inside the `getRealm` method.
    return this.getRealm<TypedRealm>().copyFromRealm(this, depth)
}
