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

package io.realm.kotlin.schema

/**
 * A [RealmClass] describing the object model of a specific class.
 */
public interface RealmClass {
    /**
     * The name of the object model.
     */
    public val name: String

    /**
     * The properties of the object model.
     */
    public val properties: Collection<RealmProperty>

    /**
     * The primary key property of the object model or `null` if the object model doesn't have a
     * primary key.
     */
    public val primaryKey: RealmProperty?

    /**
     * Returns whether or not the class is embedded.
     */
    public val isEmbedded: Boolean

    /**
     * Index operator to lookup a specific [RealmProperty] from it's property name.
     *
     * @return the [RealmProperty] with the given `propertyName` or `null` if no such property exists.
     */
    public operator fun get(key: String): RealmProperty?
}
