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

import io.realm.internal.schema.RealmSchemaImpl

interface MutableRealmSchema: RealmSchema {
    override val classes: MutableSet<MutableRealmClass>
    // Convenience method for quick lookup (internally probably stored in map)
    override operator fun get(key: String): MutableRealmClass
    // - Add/remove: Could easily be achieved as operations on `classes: MutableSet<MutableRealmClass>`
    // - Rename: Needs specific rename that doesn't remove data, so should probably be triggered on
    // setting class.name instead
}

// FIXME WIP Just to try out migration
fun MutableRealmSchema(schema: RealmSchema): MutableRealmSchema {
    return (schema as RealmSchemaImpl).copy()
}
