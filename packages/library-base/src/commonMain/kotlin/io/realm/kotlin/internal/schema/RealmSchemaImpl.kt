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

import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmPointer
import io.realm.kotlin.schema.RealmSchema

internal data class RealmSchemaImpl(
    override val classes: Collection<RealmClassImpl>
) : RealmSchema {

    override fun get(className: String): RealmClassImpl? = classes.firstOrNull { it.name == className }

    companion object {
        private fun fromRealm(dbPointer: RealmPointer, schemaMetadata: SchemaMetadata?): RealmSchemaImpl {
            val classKeys = RealmInterop.realm_get_class_keys(dbPointer)
            return RealmSchemaImpl(
                classKeys.mapNotNull {
                    val table = RealmInterop.realm_get_class(dbPointer, it)
                    val classMetadata: ClassMetadata? = schemaMetadata?.get(table.name)
                    if (schemaMetadata == null || classMetadata?.isUserDefined() == true) {
                        val properties: List<PropertyInfo> = RealmInterop.realm_get_class_properties(
                            dbPointer,
                            it,
                            table.numProperties + table.numComputedProperties
                        ).filter { property: PropertyInfo ->
                            schemaMetadata == null || classMetadata?.get(property.name)?.isUserDefined() == true
                        }
                        RealmClassImpl(table, properties)
                    } else {
                        null
                    }
                }
            )
        }

        fun fromDynamicRealm(dbPointer: RealmPointer): RealmSchemaImpl {
            return fromRealm(dbPointer, null)
        }

        fun fromTypedRealm(dbPointer: RealmPointer, schemaMetadata: SchemaMetadata): RealmSchemaImpl {
            return fromRealm(dbPointer, schemaMetadata)
        }
    }
}
