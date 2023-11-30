package io.realm.kotlin.ext

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.internal.realmObjectReference
import io.realm.kotlin.internal.realmProjectionCompanionOrNull
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmProjectionFactory
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

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

/**
 * TODO Docs
 */
public fun <O : TypedRealmObject, T: Any> RealmResults<O>.projectInto(target: KClass<T>): List<T> {
    // TODO Should this also automatically release the pointer for the results object after finishing the
    //  projection? I would be leaning towards yes, as I suspect this is primary use case. But if
    //  enough use cases show up for keeping the backing object around, we can add a
    //  `releaseRealmObjectAfterUse` boolean with a default value of `true` to this this method.
    val projectionFactory: RealmProjectionFactory<O, T>? = target.realmProjectionCompanionOrNull()
    return projectionFactory?.let { factory ->
        this.map { obj: O ->
            projectionFactory.createProjection(obj).also {
                obj.realmObjectReference?.objectPointer?.release()
            }
        }
    } ?: throw IllegalStateException("TODO")
}

