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

package io.realm.kotlin.internal.schema

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmPointer
import io.realm.kotlin.schema.RealmSchema

internal data class RealmSchemaImpl(
    override val classes: Collection<RealmClassImpl>
) : RealmSchema {

    override fun get(className: String): RealmClassImpl? = classes.firstOrNull { it.name == className }

    companion object {
        fun fromRealm(dbPointer: RealmPointer): RealmSchemaImpl {
            val classKeys = RealmInterop.realm_get_class_keys(dbPointer)
            return RealmSchemaImpl(
                classKeys.map {
                    val table = RealmInterop.realm_get_class(dbPointer, it)
                    val properties =
                        RealmInterop.realm_get_class_properties(dbPointer, it, table.numProperties + table.numComputedProperties)
                    RealmClassImpl(table, properties)
                }
            )
        }
    }
}
