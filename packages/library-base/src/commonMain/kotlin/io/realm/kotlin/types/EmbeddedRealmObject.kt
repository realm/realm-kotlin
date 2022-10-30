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

package io.realm.kotlin.types

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.RealmObjectInternal

/**
 * Marker interface to define an embedded model.
 *
 * Embedded objects have a slightly different behavior than normal objects:
 * - They must have exactly 1 parent linking to them when the embedded object is added to
 *   the Realm. Embedded objects can be the parent of other embedded objects. The parent
 *   cannot be changed later, except by copying the object.
 * - They cannot have fields annotated with `@PrimaryKey`.
 * - When a parent object is deleted, all embedded objects are also deleted.
 */
public interface EmbeddedRealmObject : TypedRealmObject

/**
 * TODO Put this in BaseRealmObject and just throw for DynamicRealmObject? Right now we duplicate this between RealmObject and EmbeddedRealmObject
 */
public inline fun <reified T : EmbeddedRealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    // TODO Better type checks
    val obj = this as RealmObjectInternal
    val realm = obj.io_realm_kotlin_objectReference!!.owner.owner as TypedRealm
    return realm.copyFromRealm(obj, depth, closeAfterCopy) as T
}

