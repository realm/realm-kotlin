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

package io.realm

import io.realm.query.RealmQuery

/**
 * A **dynamic mutable realm** gives access and allows creation and modification of data in the
 * realm through a generic string based API instead of the conventional [Realm] API that uses the
 * schema classes supplied in the configuration.
 *
 * *NOTE:* All objects obtained from a [DynamicRealm] are only valid in the scope of the dynamic
 * realm. Thus they cannot be passed outside of an [RealmMigration] that gives access to a specific
 * [DynamicRealm] instance, etc.
 */
interface DynamicMutableRealm : DynamicRealm {

    /**
     * Adds and returns a new object of the specified class to the Realm.
     *
     * @param type the class name of the object to create.
     * @return the new object.
     * @throws IllegalArgumentException if the class name is not part of the realm's schema or the
     * class requires a primary key.
     */
    fun createObject(type: String): DynamicMutableRealmObject

    /**
     * Adds and returns a new object of the specified class with the given primary key to the Realm.
     *
     * @param type the class name of the object to create.
     * @param primaryKey the primary key value.
     * @return the new object.
     * @throws IllegalArgumentException if the class name is not part of the realm's schema or the
     * class
     */
    fun createObject(type: String, primaryKey: Any?): DynamicMutableRealmObject

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
     * Find an object in this realm from an object from another version of the realm.
     *
     * This makes it possible to get a mutable realm object from an
     * older version of the object, most notably as part of an [AutomaticSchemaMigration].
     *
     * @param obj realm object to look up, or `null` if the object has been deleted in this realm.
     */
    fun findLatest(obj: RealmObject): DynamicMutableRealmObject?

    // FIXME Do we want an explicit delete here? At least we should probably match MutableRealm.delete/RealmObject.delete behaviour
    //  https://github.com/realm/realm-kotlin/issues/181
    // fun delete(obj: RealmObject)
}
