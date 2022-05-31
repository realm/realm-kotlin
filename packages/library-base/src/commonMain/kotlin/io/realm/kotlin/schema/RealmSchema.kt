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
 * A **schema** that describes the object model of the underlying realm.
 */
// FIXME Find out where the transaction version of the schema fits into the API ... maybe as part of
//  https://github.com/realm/realm-kotlin/issues/600
public interface RealmSchema {
    /**
     * Collection of the [RealmClass]es of the schema.
     */
    public val classes: Collection<RealmClass>

    /**
     * Index operator to lookup a specific [RealmClass] from it's class name.
     *
     * @return the [RealmClass] with the given `className` or `null` if no such class exists.
     */
    public operator fun get(className: String): RealmClass?
}
