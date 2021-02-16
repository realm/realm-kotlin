/*
 * Copyright 2020 Realm Inc.
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

package io.realm

import io.realm.interop.Property
import io.realm.interop.PropertyType
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.interop.Table
import io.realm.runtimeapi.Mediator
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmCompanion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RealmConfiguration private constructor(
    val path: String?, // Full path if we don't want to use the default location
    val name: String?, // Optional Realm name (default is 'default')
    val schema: Mediator, // TODO create a schema type, to fail at compile time?
    val version: Long = 0,
    val tables: List<Table> = listOf()
) {

    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()

    init {
        RealmInterop.realm_config_set_path(nativeConfig, path!!)
        RealmInterop.realm_config_set_schema_mode(nativeConfig, SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC)
        RealmInterop.realm_config_set_schema_version(nativeConfig, version)
        val schema = RealmInterop.realm_schema_new(tables)
        RealmInterop.realm_config_set_schema(nativeConfig, schema)
    }

    data class Builder(
        var path: String? = null, // Full path for Realm (directory + name)
        var name: String = "default", // Optional Realm name (default is 'default')
        var schema: Any? = null,
        var classes: List<RealmCompanion> = listOf()
    ) {
        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(schema: Any) = apply { this.schema = schema }

        fun classes(classes: List<RealmCompanion>) = apply { this.classes = classes }
        fun build(): RealmConfiguration {
            if (path == null) {
                val directory = PlatformHelper.appFilesDirectory()
                // FIXME Proper platform agnostic file separator: File.separator is not available for Kotlin/Native
                //  https://github.com/realm/realm-kotlin/issues/75
                path = "$directory/$name.realm"
            }
            if (schema !is Mediator) {
                error("schema parameter should be a class annotated with @RealmModule")
            }
            return RealmConfiguration(
                path, name, schema as Mediator,
                tables = (schema as Mediator).schema().map { parseSchema(it as String) }
            )
        }

        // Highly explosive. Quick implementation to overcome that we don't have typed schemas in the compantion objects yet
        private fun parseSchema(schema: String): Table {
            val table: JsonObject = Json.parseToJsonElement(schema).jsonObject
            val name1 = table["name"]!!.jsonPrimitive.content
            val properties = table["properties"]!!.jsonArray.toList().map { element: JsonElement ->
                val property = element as JsonObject
                @Suppress("TooGenericExceptionThrown")
                if (property.keys.size != 1) throw RuntimeException("Malformed schema: $schema")
                val name = property.keys.first()
                val attributes = property[name]!!.jsonObject
                val type = when (attributes["type"]!!.jsonPrimitive.content) {
                    "string" -> PropertyType.RLM_PROPERTY_TYPE_STRING
                    "int" -> PropertyType.RLM_PROPERTY_TYPE_INT
                    "boolean" -> PropertyType.RLM_PROPERTY_TYPE_BOOL
                    "float" -> PropertyType.RLM_PROPERTY_TYPE_FLOAT
                    "double" -> PropertyType.RLM_PROPERTY_TYPE_DOUBLE
                    else -> error("Unsupported type ${attributes["type"]!!.jsonPrimitive.content}")
                }
                Property(name = name, type = type)
            }

            return Table(name1, properties = properties)
        }
    }
}
