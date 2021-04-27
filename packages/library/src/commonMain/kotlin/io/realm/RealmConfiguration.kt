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
import io.realm.internal.RealmModelInternal
import io.realm.internal.RealmObjectCompanion
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import kotlin.reflect.KClass

class RealmConfiguration(
    val path: String? = null, // Full path if we don't want to use the default location
    val name: String = "default", // Optional Realm name (default is 'default')
    schema: Set<KClass<out RealmObject>> // classes literal (T::class)
) {
    internal var mapOfKClassWithCompanion: Map<KClass<*>, RealmObjectCompanion> = emptyMap()
    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()
    internal lateinit var mediator: Mediator

    // called by the compiler plugin, with a populated companion map
    internal constructor (path: String?, name: String = "default", companionMap: Map<KClass<*>, RealmObjectCompanion>) :
        this(path, name, emptySet()) {
            mapOfKClassWithCompanion = companionMap
            init()
        }

    class Builder(
        var path: String? = null, // Full path for Realm (directory + name)
        var name: String = "default", // Optional Realm name (default is 'default')
        vararg var schema: KClass<out RealmObject>
    ) {
        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(vararg classes: KClass<out RealmObject>) = apply { this.schema = classes }

        fun build(): RealmConfiguration {
            @Suppress("SpreadOperator")
            return RealmConfiguration(path, name, setOf(*schema))
        }

        // Called from compiler plugin
        internal fun build(companionMap: Map<KClass<*>, RealmObjectCompanion>): RealmConfiguration {
            return RealmConfiguration(path, name, companionMap)
        }
    }

    private fun init() {
        val internalPath = if (path == null || path.isEmpty()) {
            val directory = PlatformHelper.appFilesDirectory()
            // FIXME Proper platform agnostic file separator: File.separator is not available for Kotlin/Native
            //  https://github.com/realm/realm-kotlin/issues/75
            "$directory/$name.realm"
        } else path

        RealmInterop.realm_config_set_path(nativeConfig, internalPath)
        RealmInterop.realm_config_set_schema_mode(
            nativeConfig,
            SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
        )
        RealmInterop.realm_config_set_schema_version(nativeConfig, version = 0) // TODO expose version when handling migration modes
        val schema = RealmInterop.realm_schema_new(mapOfKClassWithCompanion.values.map { it.`$realm$schema`() })
        RealmInterop.realm_config_set_schema(nativeConfig, schema)

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<*>): RealmModelInternal = (
                mapOfKClassWithCompanion[clazz]?.`$realm$newInstance`()
                    ?: error("$clazz not part of this configuration schema")
                ) as RealmModelInternal

            override fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion = mapOfKClassWithCompanion[clazz]
                ?: error("$clazz not part of this configuration schema")
        }
    }
}
