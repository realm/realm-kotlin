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

interface MutableRealmClass : RealmClass {
    override var name: String // Rename would be a matter of updating the name
    // TODO Not supported yet
    // override var embedded: Boolean
    override val properties: MutableSet<MutableRealmProperty>
    // Convenience method for quick lookup (internally probably stored in map)
    override operator fun get(key: String): MutableRealmProperty?
    // Derived attributes, can change on updates to properties
    override fun primaryKey(): MutableRealmProperty? // convenience for:  properties.any { it.primaryKey }
}
