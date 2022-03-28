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

package io.realm.internal

import io.realm.Deleteable

// TODO Public due to being a transitive dependency of RealmInternalObject
public interface InternalDeleteable : Deleteable {
    public fun delete()
}

internal fun Deleteable.asInternalDeleteable(): InternalDeleteable {
    return when (this) {
        // InternalDeleteable is not on RealmObjects but on the RealmObjectReference
        is RealmObjectInternal ->
            this.realmObjectReference
                ?: throw IllegalArgumentException("Cannot delete unmanaged object")
        is InternalDeleteable -> this
        else ->
            // This should only happen if users implements Deleteable and pass their non-Realm objects
            // to delete
            throw IllegalArgumentException("Cannot delete custom Deleteable objects: ${this::class.simpleName}")
    }
}
