package io.realm

import io.realm.query.RealmQuery
import kotlin.reflect.KClass

/**
 * Represents the writeable state of a Realm file.
 *
 * To modify data in a [Realm], use instances of this class.
 * These are provided and managed automatically through either [Realm.write] or
 * [Realm.writeBlocking].
 *
 * All objects created and/or obtained from the _mutable realm_ in a write-transaction are bound to
 * the thread executing the transaction. All operations on the _mutable realm_ or on any of the
 * objects contained in that realm must execute on the thread executing the transaction. The only exception is objects returned
 * from [Realm.write] and [Realm.writeBlocking], which are frozen and remain tied to the resulting
 * version of the write-transaction.
 */
interface MutableRealm : TypedRealm {
    /**
     * Get latest version of an object.
     *
     * Realm write transactions always operate on the latest version of data. This method
     * makes it possible to easily find the latest version of any frozen Realm Object and
     * return a copy of it that can be modified while inside the write block.
     *
     * *Note:* This object is not readable outside the write block unless it has been explicitly
     * returned from the write.
     *
     * @param obj realm object to look up. Its latest state will be returned. If the object
     * has been deleted, `null` will be returned.
     *
     * @throws IllegalArgumentException if called on an unmanaged object.
     */
    fun <T : RealmObject> findLatest(obj: T): T?

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     *
     * @throws IllegalStateException if the write transaction was already cancelled.
     */
    fun cancelWrite()

    /**
     * Creates a copy of an object in the Realm.
     *
     * This will create a copy of an object and all it's children. Any already managed objects will
     * not be copied, including the root `instance`. So invoking this with an already managed
     * object is a no-operation.
     *
     * @param instance the object to create a copy from.
     * @return the managed version of the `instance`.
     *
     * @throws IllegalArgumentException if the class has a primary key field and an object with the same
     * primary key already exists.
     */
    fun <T : RealmObject> copyToRealm(instance: T): T

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * The result is live and thus also reflects any update to the [MutableRealm].
     *
     * The result is only valid on the calling thread.
     *
     * @param clazz the class of the objects to query for.
     * @return the result of the query, reflecting future updates to the mutable realm.
     */
    override fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T>

    /**
     * Returns a [RealmQuery] matching the predicate represented by [query].
     *
     * The results yielded by the query are live and thus also reflect any update to the
     * [MutableRealm]. Said results are only valid on the calling thread.
     *
     * **It is not allowed to call [RealmQuery.asFlow] on queries generated from a [MutableRealm].**
     *
     * The resulting query is lazily evaluated and will not perform any calculations until
     * [RealmQuery.find] is called.
     *
     * @param query the Realm Query Language predicate to append.
     * @param args Realm values for the predicate.
     */
    override fun <T : RealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T>

    /**
     * Deletes the object from the underlying Realm.
     *
     * @throws IllegalArgumentException if the object is not managed by Realm.
     */
    fun <T : RealmObject> delete(obj: T)
}

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper for [MutableRealm.query].
 */
inline fun <reified T : RealmObject> MutableRealm.query(
    query: String = "TRUEPREDICATE",
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)
