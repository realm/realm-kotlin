/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.dynamic

import io.realm.kotlin.Deleteable
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject

/**
 * A **dynamic mutable realm** gives access and allows creation and modification of data in the
 * realm through a generic string based API instead of the conventional [Realm] API that uses the
 * typed API of the schema classes supplied in the configuration.
 */
public interface DynamicMutableRealm : DynamicRealm {

    /**
     * Copy new objects into the realm or update existing objects.
     *
     * This will recursively copy objects to the realm. Both those with and without primary keys.
     * The behavior of copying objects with primary keys will depend on the specified update
     * policy. Calling with [UpdatePolicy.ERROR] will disallow updating existing objects. So if
     * an object with the same primary key already exists, an error will be thrown. Setting this
     * thus means that only new objects can be created. Calling with [UpdatePolicy.ALL] means
     * that an existing object with a matching primary key will have all its properties updated with
     * the values from the input object.
     *
     * Already managed up-to-date objects will not be copied but just return the instance
     * itself. Trying to copy outdated objects will throw an exception. To get hold of an updated
     * reference for an object use [findLatest].
     *
     * @param instance the object to create a copy from.
     * @param updatePolicy update policy when importing objects.
     * @return the managed version of the `instance`.
     *
     * @throws IllegalArgumentException if the object graph of `instance` either contains an object
     * with a primary key value that already exists and the update policy is [UpdatePolicy.ERROR],
     * if the object graph contains an object from a previous version or if a property does not
     * match the underlying schema.
     */
    public fun copyToRealm(obj: BaseRealmObject, updatePolicy: UpdatePolicy = UpdatePolicy.ERROR): DynamicMutableRealmObject

    /**
     * Returns a query for dynamic mutable realm objects of the specified class.
     *
     * @param className the name of the class of which to query for.
     * @param query the Realm Query Language predicate use when querying.
     * @param args realm values for the predicate.
     * @return a RealmQuery, which can be used to query for specific objects of provided type.
     * @throws IllegalArgumentException if the class with `className` doesn't exist in the realm.
     *
     * @see DynamicMutableRealmObject
     */
    override fun query(className: String, query: String, vararg args: Any?): RealmQuery<DynamicMutableRealmObject>

    /**
     * Get latest version of an object.
     *
     * This makes it possible to get a mutable realm object from an
     * older version of the object, most notably as part of an [AutomaticSchemaMigration].
     *
     * @param obj realm object to look up
     * @returns a [DynamicMutableRealmObject] reference to the object version as of this realm or
     * `null` if the object has been deleted in this realm.
     */
    public fun findLatest(obj: BaseRealmObject): DynamicMutableRealmObject?

    /**
     * Delete objects from the underlying Realm.
     *
     * [RealmObject], [RealmList], [RealmQuery], [RealmSingleQuery] and [RealmResults] can be
     * deleted this way.
     *
     * *NOTE:* Only live objects can be deleted. Frozen objects must be resolved in the current
     * context using [MutableRealm.findLatest]:
     *
     * ```
     * val frozenObj = realm.query<Sample>.first().find()
     * realm.write {
     *   findLatest(frozenObject)?.let { delete(it) }
     * }
     * ```
     *
     * @param the [RealmObject], [RealmList], [RealmQuery], [RealmSingleQuery] or [RealmResults] to delete.
     * @throws IllegalArgumentException if the object is invalid, frozen or not managed by Realm.
     */
    public fun delete(deleteable: Deleteable)
}
