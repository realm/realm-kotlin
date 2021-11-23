package io.realm

import kotlin.reflect.KClass

/**
 * A **typed realm** that can be queried for objects of a specific type.
 */
interface TypedRealm : BaseRealm {

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * For a [Realm] instance this reflects the state of the realm at the invocation time, thus
     * the results will not change on updates to the Realm. For a [MutableRealm] the result is live
     * and will in fact reflect updates to the [MutableRealm].
     *
     * @param clazz The class of the objects to query for.
     * @return The result of the query.
     */
    open fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T>

    /**
     * TODO : query
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
