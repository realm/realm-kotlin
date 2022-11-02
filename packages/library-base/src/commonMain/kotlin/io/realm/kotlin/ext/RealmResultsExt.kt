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
 * @param closeAfterCopy Whether or not to close Realm objects after they have been copied (default
 * is `true`). This includes the [RealmResults] itself. Closed objects are no longer valid and
 * accessing them will throw an [IllegalStateException]. This can be beneficial as managed
 * RealmObjects contain a reference to a chunck of native memory. This memory is normally freed when
 * the object is garbage collected by Kotlin. However, manually closing the object allow Realm to
 * free that memory immediately, allowing for better native memory management and control over the
 * size of the Realm file.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <reified T : TypedRealmObject> RealmResults<T>.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): List<T> {
    // We don't have unmanaged RealmResults in the API and `getRealm` will throw an exception if
    // the Realm is closed, so all error handling is done inside the `getRealm` method.
    return this.getRealm<TypedRealm>().copyFromRealm(this, depth, closeAfterCopy)
}
