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

package io.realm.dynamic

import io.realm.RealmObject
import io.realm.query.RealmQuery

/**
 * A **dynamic mutable realm** gives access and allows creation and modification of data in the
 * realm through a generic string based API instead of the conventional [Realm] API that uses the
 * typed API of the schema classes supplied in the configuration.
 */
public interface DynamicMutableRealm : DynamicRealm {

    /**
     * Adds and returns a new object of the specified class to the Realm.
     *
     * All fields will have default values; `""` for strings, `0` for integral types, etc. and
     * `null` for nullable fields.
     *
     * @param type the class name of the object to create.
     * @return the new object.
     * @throws IllegalArgumentException if the class name is not part of the realm's schema or the
     * class requires a primary key.
     */
    public fun createObject(type: String): DynamicMutableRealmObject

    /**
     * Adds and returns a new object of the specified class with the given primary key to the Realm.
     *
     * All fields will have default values; `""` for strings, `0` for integral types, etc. and
     * `null` for nullable fields.
     *
     * @param type the class name of the object to create.
     * @param primaryKey the primary key value.
     * @return the new object.
     * @throws IllegalArgumentException if the class name is not part of the realm's schema or the
     * primary key is not of the correct type.
     */
    public fun createObject(type: String, primaryKey: Any?): DynamicMutableRealmObject

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
    public fun findLatest(obj: RealmObject): DynamicMutableRealmObject?

    // FIXME Align delete behavior with MutableRealm
    //  https://github.com/realm/realm-kotlin/issues/181
    // fun delete(obj: RealmObject)
}
