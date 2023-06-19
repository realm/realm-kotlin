/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.types.AsymmetricRealmObject

/**
 * Insert a [AsymmetricRealmObject] into Realm. Since asymmetric objects are "write-only", it is
 * not possible to access the managed data after it has been inserted.
 *
 * @param obj the object to insert.
 * @throws IllegalArgumentException if the object graph of [obj] either contains an object
 * with a primary key value that already exists, if the object graph contains an object from a
 * previous version or if a property does not match the underlying schema.
 */
public fun <T : AsymmetricRealmObject> MutableRealm.insert(obj: T) {
    @Suppress("invisible_member", "invisible_reference")
    if (this is io.realm.kotlin.internal.InternalMutableRealm) {
        io.realm.kotlin.internal.copyToRealm(
            configuration.mediator,
            realmReference,
            obj,
            UpdatePolicy.ERROR
        )
    } else {
        throw IllegalStateException("Calling insert() on $this is not supported.")
    }
}
