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
     * @param obj object to copy.
     * @param depth limit of the deep copy. All object references after this depth will be `null`
     * and [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
     * @param closeAfterCopy managed RealmObjects are lazily reading their data from the underlying
     * database. This requires that they t
     * @returns a in-memory copy of the input object.
     * @throws IllegalArgumentException if depth < 0 or [obj] is not a valid object to copy.
     */
    public fun <T : TypedRealmObject> copyFromRealm(obj: T, depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T

    /**
     * TODO
     * This will also allow DynamicRealmObjects (figure out how to fix this)
     */
    public fun <T : TypedRealmObject> copyFromRealm(obj: Iterable<T>, depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): List<T>
}
