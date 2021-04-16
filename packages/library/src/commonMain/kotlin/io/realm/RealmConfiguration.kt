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

import io.realm.internal.Mediator
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.interop.Table

class RealmConfiguration private constructor(
    val path: String?, // Full path if we don't want to use the default location
    val name: String?, // Optional Realm name (default is 'default')
    val schema: Mediator,
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
        var name: String = "default.realm", // Optional Realm name (default is 'default')
        var schema: Any? = null,
    ) {
        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(schema: Any) = apply { this.schema = schema }

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
                tables = (schema as Mediator).schema()
            )
        }
    }
}
