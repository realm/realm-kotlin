package io.realm

import kotlin.reflect.KClass

/**
 * A **typed realm** that can be queried for objects of a specific type.
 */
interface TypedRealm : BaseRealm {

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * For a [Realm] instance this reflects the state of the Realm at the invocation time, thus
     * the results will not change on updates to the Realm. For a [MutableRealm] the result is live
     * and will in fact reflect updates to the [MutableRealm].
     *
     * @param clazz The class of the objects to query for.
     * @return The result of the query.
     */
    open fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T>

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
    fun <T : RealmObject> query(
        clazz: KClass<T>,
        query: String = "TRUEPREDICATE",
        vararg args: Any?
    ): RealmQuery<T>
}

/**
 * Returns the results of querying for all objects of a specific type.
 *
 * Reified convenience wrapper of [TypedRealm.objects].
 *
 * @param T Type of the object to query for.
 * @return The result of the query.
 */
inline fun <reified T : RealmObject> TypedRealm.objects(): RealmResults<T> {
    return this.objects(T::class)
}
