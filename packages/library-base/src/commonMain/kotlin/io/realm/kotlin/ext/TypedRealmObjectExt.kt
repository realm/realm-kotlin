package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.RealmObject

/**
 * Makes an unmanaged in-memory copy of an already persisted [io.realm.kotlin.types.RealmObject].
 * This is a deep copy that will copy all referenced objects.
 *
 * @param obj managed object to copy from the Realm.
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @param closeAfterCopy Whether or not to close a Realm object after it has been copied. If
 * an object is closed, `RealmObject.isValid()` will return `false` and further access to it
 * will throw an [IllegalStateException]. This can be beneficial as managed RealmObjects contain
 * a reference to a chunck of native memory. This memory is normally freed when the object is
 * garbage collected by Kotlin. However, manually closing the object allow Realm to free that
 * memory immediately, allowing for better native memory management and control over the size
 * of the Realm file.
 * @returns a in-memory copy of the input object.
 * @throws IllegalArgumentException if depth < 0 or the object  is not a valid object to copy.
 */
public inline fun <reified T : RealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth, closeAfterCopy)
    } ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
