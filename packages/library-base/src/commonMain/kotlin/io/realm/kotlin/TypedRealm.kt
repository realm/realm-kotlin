package io.realm.kotlin

import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * A **typed realm** that can be queried for objects of a specific type.
 */
public interface TypedRealm : BaseRealm {

    /**
     * Returns a [RealmQuery] matching the predicate represented by [query].
     *
     * For a [Realm] instance this reflects the state of the Realm at the invocation time, this
     * the results obtained from the query will not change on updates to the Realm. For a
     * [MutableRealm] the query will produce live results and will in fact reflect updates to the
     * [MutableRealm].
     *
     * @param query the Realm Query Language predicate to append.
     * @param args Realm values for the predicate.
     */
    public fun <T : BaseRealmObject> query(
        clazz: KClass<T>,
        query: String = "TRUEPREDICATE",
        vararg args: Any?
    ): RealmQuery<T>

    /**
     * Makes an unmanaged in-memory copy of an already persisted [io.realm.kotlin.types.RealmObject].
     * This is a deep copy that will copy all referenced objects.
     *
     * @param obj managed object to copy from the Realm.
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
     * @param closeAfterCopy Whether or not to close a Realm object after it has been copied (default
     * is `false`). If an object is closed, `RealmObject.isValid()` will return `false` and further
     * access to it will throw an [IllegalStateException]. This can be beneficial as managed
     * RealmObjects contain a reference to a chunck of native memory. This memory is normally freed
     * when the object is garbage collected by Kotlin. However, manually closing the object allow
     * Realm to free that memory immediately, allowing for better native memory management and
     * control over the size of the Realm file.
     * @returns an in-memory copy of the input object.
     * @throws IllegalArgumentException if [obj] is not a valid object to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(obj: T, depth: UInt = UInt.MAX_VALUE, closeAfterCopy: Boolean = false): T

    /**
     * Makes an unmanaged in-memory copy of a collection of already persisted
     * [io.realm.kotlin.types.RealmObject]s. This is a deep copy that will copy all
     * referenced objects.
     *
     * @param collection the list of objects to copy. The collection itself does not need to be
     * managed by Realm, but can eg. be a normal unmanaged [List] or [Set]. Only requirement is
     * that all objects inside the collection are managed by Realm.
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
     * @param closeAfterCopy Whether or not to close Realm objects after they have been copied (default
     * is `false`). This includes the [collection], so if a managed collection like a [RealmList] or
     * [RealmResults] is passed in, it will also be closed. Closed objects are no longer valid and
     * accessing them will throw an [IllegalStateException]. This can be beneficial as managed
     * RealmObjects contain a reference to a chunck of native memory. This memory is normally freed
     * when the object is garbage collected by Kotlin. However, manually closing the object allow
     * Realm to free that memory immediately, allowing for better native memory management and
     * control over the size of the Realm file.
     * @returns an in-memory copy of all input objects.
     * @throws IllegalArgumentException if the [collection] is not valid or contains objects that
     * are not valid to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(collection: Iterable<T>, depth: UInt = UInt.MAX_VALUE, closeAfterCopy: Boolean = false): List<T>
}
