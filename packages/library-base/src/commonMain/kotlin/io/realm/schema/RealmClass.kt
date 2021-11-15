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

interface RealmClass {
    val name: String
    // TODO Not supported yet
    // val embedded: Boolean
    // Alternatively as Map<String, RealmProperty>, but would require validation of key/class.name for
    // mutable schemas
    val properties: Set<RealmProperty>
    // Convenience method for quick lookup (internally probably stored in map)
    operator fun get(key: String): RealmProperty?
    // Derived attributes, can change on updates to properties
    fun primaryKey(): RealmProperty? // convenience for:  properties.any { it.primaryKey }
}
