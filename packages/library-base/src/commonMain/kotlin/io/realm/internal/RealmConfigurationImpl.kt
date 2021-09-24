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

package io.realm.internal

import io.realm.LogConfiguration
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.SchemaMode
import io.realm.internal.platform.appFilesDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

@Suppress("LongParameterList")
public class RealmConfigurationImpl internal constructor(
    companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>,
    path: String?,
    name: String,
    schema: Set<KClass<out RealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcher,
    writeDispatcher: CoroutineDispatcher,
    schemaVersion: Long,
    deleteRealmIfMigrationNeeded: Boolean,
    encryptionKey: ByteArray?,
) : RealmConfiguration {

    override val path: String

    override val name: String

    override val schema: Set<KClass<out RealmObject>>

    override val log: LogConfiguration

    override val maxNumberOfActiveVersions: Long

    val notificationDispatcher: CoroutineDispatcher

    val writeDispatcher: CoroutineDispatcher

    override val schemaVersion: Long

    override val deleteRealmIfMigrationNeeded: Boolean

    override val encryptionKey get(): ByteArray? = RealmInterop.realm_config_get_encryption_key(nativeConfig)

    // Internal properties used by other Realm components, but does not make sense for the end user to know about
    internal var mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>
    internal var mediator: Mediator

    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()

    init {
        this.path = if (path == null || path.isEmpty()) {
            val directory = appFilesDirectory()
            // FIXME Proper platform agnostic file separator: File.separator is not available for Kotlin/Native
            //  https://github.com/realm/realm-kotlin/issues/75
            "$directory/$name"
        } else path
        this.name = name // FIXME Should read name from end of path
        this.schema = schema
        this.mapOfKClassWithCompanion = companionMap
        this.log = logConfig
        this.maxNumberOfActiveVersions = maxNumberOfActiveVersions
        this.notificationDispatcher = notificationDispatcher
        this.writeDispatcher = writeDispatcher
        this.schemaVersion = schemaVersion
        this.deleteRealmIfMigrationNeeded = deleteRealmIfMigrationNeeded

        RealmInterop.realm_config_set_path(nativeConfig, this.path)

        when (deleteRealmIfMigrationNeeded) {
            true -> SchemaMode.RLM_SCHEMA_MODE_RESET_FILE
            false -> SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
        }.also { schemaMode ->
            RealmInterop.realm_config_set_schema_mode(nativeConfig, schemaMode)
        }

        RealmInterop.realm_config_set_schema_version(config = nativeConfig, version = schemaVersion)

        val nativeSchema = RealmInterop.realm_schema_new(mapOfKClassWithCompanion.values.map { it.`$realm$schema`() })

        RealmInterop.realm_config_set_schema(nativeConfig, nativeSchema)
        RealmInterop.realm_config_set_max_number_of_active_versions(nativeConfig, maxNumberOfActiveVersions)

        encryptionKey?.let {
            RealmInterop.realm_config_set_encryption_key(nativeConfig, it)
        }

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<*>): RealmObjectInternal = (
                mapOfKClassWithCompanion[clazz]?.`$realm$newInstance`()
                    ?: error("$clazz not part of this configuration schema")
                ) as RealmObjectInternal

            override fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion =
                mapOfKClassWithCompanion[clazz]
                    ?: error("$clazz not part of this configuration schema")
        }
    }
}
