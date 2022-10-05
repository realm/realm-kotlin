/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm.kotlin

import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
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
    public fun <T : BaseRealmObject> findLatest(obj: T): T?

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     *
     * @throws IllegalStateException if the write transaction was already cancelled.
     */
    public fun cancelWrite()

    /**
     * Copy new objects into the realm or update existing objects.
     *
     * This will recursively copy objects to the realm. Both those with and without primary keys.
     * The behavior of copying objects with primary keys will depend on the specified update
     * policy. Calling with [UpdatePolicy.ERROR] will disallow updating existing objects. So if
     * an object with the same primary key already exists, an error will be thrown. Setting this
     * thus means that only new objects can be created. Calling with [UpdatePolicy.ALL] mean
     * that an existing object with a matching primary key will have all its properties updated with
     * the values from the input object.
     *
     * Already managed update-to-date objects will not be copied but just return the instance
     * itself. Trying to copy outdated objects will throw an exception. To get hold of an updated
     * reference for an object use [findLatest].
     *
     * @param instance the object to create a copy from.
     * @param updatePolicy update policy when importing objects.
     * @return the managed version of the `instance`.
     *
     * @throws IllegalArgumentException if the object graph of `instance` either contains an object
     * with a primary key value that already exists and the update policy is [UpdatePolicy.ERROR] or
     * if the object graph contains an object from a previous version.
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
    override fun <T : BaseRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T>

    /**
     * Delete objects from the underlying Realm.
     *
     * [RealmObject], [EmbeddedRealmObject], [RealmList], [RealmQuery], [RealmSingleQuery] and
     * [RealmResults] can be deleted this way.
     *
     * *NOTE:* Only live objects can be deleted. Frozen objects must be resolved in the current
     * context by using [MutableRealm.findLatest]:
     *
     * ```
     * val frozenObj = realm.query<Sample>.first().find()
     * realm.write {
     *   findLatest(frozenObject)?.let { delete(it) }
     * }
     * ```
     *
     * @param deleteable the [RealmObject], [EmbeddedRealmObject], [RealmList], [RealmQuery],
     * [RealmSingleQuery] or [RealmResults] to delete.
     * @throws IllegalArgumentException if the object is invalid, frozen or not managed by Realm.
     */
    public fun delete(deleteable: Deleteable)

    /**
     * Deletes all objects from this Realm.
     */
    public fun deleteAll()

    /**
     * Deletes all objects of the specified class from the Realm.
     *
     * @param KClass the class whose objects should be removed.
     * @throws IllegalStateException if the class does not exist within the schema.
     */
    public fun delete(schemaClass: KClass<out BaseRealmObject>)
}

/**
 * Deletes all objects of the specified class from the Realm.
 *
 * Reified convenience wrapper of [MutableRealm.delete].
 */
public inline fun <reified T : BaseRealmObject> MutableRealm.delete() {
    delete(T::class)
}
