/*
 * Copyright 2021 Realm Inc.
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

package io.realm.schema

/**
 * A [RealmClass] describes the object model of a specific class.
 */
interface RealmClass { // Matches realm_class_info_t except that attributes are also attached here
    /**
     * Returns the name of the class in the object model.
     */
    val name: String

    /**
     * Returns the properties of the class in the object model.
     */
    val properties: Collection<RealmProperty>

    /**
     * Index operator to lookup a specific [RealmProperty] from it's property name.
     *
     * @return the [RealmProperty] with the given `propertyName` or `null` if no such property exists.
     */
    operator fun get(key: String): RealmProperty?

    /**
     * Return the class' primary key [RealmProperty].
     *
     * @returnn the class' primary key [RealmProperty] or `null` it the class doesn't have a primary
     * key.
     */
    // If this is never to change we could make it a val, but if using same API for mutable schema
    // it could actually change
    fun primaryKey(): RealmProperty? // equivalent to:  properties.any { it.primaryKey }
}
