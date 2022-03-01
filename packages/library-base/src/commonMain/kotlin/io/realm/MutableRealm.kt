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
public interface MutableRealm : TypedRealm {
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
    // TODO Should actually be BaseRealm.find/TypedRealm.find as we should be able to resolve any
    //  object in any other version also for non-mutable realms ... maybe 'resolve' instead
    public fun <T : RealmObject> findLatest(obj: T): T?

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     *
     * @throws IllegalStateException if the write transaction was already cancelled.
     */
    public fun cancelWrite()

    /**
     * Update policy for imports with [copyToRealm].
     *
     * @see copyToRealm
     */
    // FIXME #naming
    public enum class UpdatePolicy {
        /**
         * Update policy that causes import of an object with an existing primary key to fail.
         */
        ERROR,

        /**
         * Update policy that will update any existing objects identified with the same primary key.
         */
        ALL,
    }

    /**
     * Creates a copy or update existing objects in the Realm.
     *
     * This will recursively copy non-primary key objects and non-existing primary key objects into
     * the realm. The behavior of copying existing primary key objects will depend on the specified
     * update policy. Calling with [UpdatePolicy.ERROR] will cause creating of objects with existing
     * primary key to throw, while calling with [UpdatePolicy.ALL] will update existing primary key
     * object and all it's properties.
     *
     * Already managed update-to-date objects will not be copied but just return the instance
     * itself. Trying to copy outdated objects will throw an exception. To get hold of an updated
     * reference for an object use * [findLatest].
     *
     * @param instance the object to create a copy from.
     * @param updatePolicy update policy for the import.
     * @return the managed version of the `instance`.
     *
     * @throws IllegalArgumentException if the object graph of `instance` either contains a primary
     * key object that already exists and the update policy is [UpdatePolicy.ERROR] or if the object
     * graph contains an outdated object.
     */
    public fun <T : RealmObject> copyToRealm(instance: T, updatePolicy: UpdatePolicy = UpdatePolicy.ERROR): T

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
     * Deletes the object from the underlying Realm. Only live objects can be deleted.
     *
     * Frozen objects can be converted using [MutableRealm.findLatest]:
     *
     * ```
     * val frozenObj = realm.query<Sample>.first().find()
     * realm.write {
     *   findLatest(frozenObject)?.let { delete(it) }
     * }
     * ```
     *
     * @throws IllegalArgumentException if the object is invalid, frozen or not managed by Realm.
     */
    public fun <T : RealmObject> delete(obj: T)
}

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper for [MutableRealm.query].
 */
public inline fun <reified T : RealmObject> MutableRealm.query(
    query: String = "TRUEPREDICATE",
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)
