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
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.SchemaMode
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.realmObjectCompanion
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

// TODO Public due to being accessed from `library-sync`
@Suppress("LongParameterList")
public open class ConfigurationImpl constructor(
    path: String?,
    name: String,
    schema: Set<KClass<out RealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcher,
    writeDispatcher: CoroutineDispatcher,
    schemaVersion: Long,
    schemaMode: SchemaMode,
    encryptionKey: ByteArray?,
) : InternalConfiguration {

    override val path: String

    override val name: String

    override val schema: Set<KClass<out RealmObject>>

    override val log: LogConfiguration

    override val maxNumberOfActiveVersions: Long

    override val schemaVersion: Long

    override val schemaMode: SchemaMode

    override val encryptionKey: ByteArray?
        get(): ByteArray? = RealmInterop.realm_config_get_encryption_key(nativeConfig)

    override val mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>

    override val mediator: Mediator

    override val nativeConfig: NativePointer = RealmInterop.realm_config_new()

    override val notificationDispatcher: CoroutineDispatcher

    override val writeDispatcher: CoroutineDispatcher

    init {
        this.path = if (path == null || path.isEmpty()) {
            val directory = appFilesDirectory()
            // FIXME Proper platform agnostic file separator: File.separator is not available for Kotlin/Native
            //  https://github.com/realm/realm-kotlin/issues/75
            "$directory/$name"
        } else if (path.startsWith("./")) {
            path.replaceFirst("./", "${appFilesDirectory()}/")
        } else path
        this.name = name // FIXME Should read name from end of path
        this.schema = schema
        this.mapOfKClassWithCompanion = schema.associateWith { realmObjectCompanion(it) }
        this.log = logConfig
        this.maxNumberOfActiveVersions = maxNumberOfActiveVersions
        this.notificationDispatcher = notificationDispatcher
        this.writeDispatcher = writeDispatcher
        this.schemaVersion = schemaVersion
        this.schemaMode = schemaMode

        RealmInterop.realm_config_set_path(nativeConfig, this.path)
        RealmInterop.realm_config_set_schema_mode(nativeConfig, schemaMode)
        RealmInterop.realm_config_set_schema_version(config = nativeConfig, version = schemaVersion)

        val nativeSchema = RealmInterop.realm_schema_new(
            mapOfKClassWithCompanion.values.map { it ->
                it.`$realm$schema`().let { it.cinteropClass to it.cinteropProperties }
            }
        )

        RealmInterop.realm_config_set_schema(nativeConfig, nativeSchema)
        RealmInterop.realm_config_set_max_number_of_active_versions(nativeConfig, maxNumberOfActiveVersions)

        encryptionKey?.let {
            RealmInterop.realm_config_set_encryption_key(nativeConfig, it)
        }

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<out RealmObject>): RealmObjectInternal =
                companionOf(clazz).`$realm$newInstance`() as RealmObjectInternal

            override fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion =
                mapOfKClassWithCompanion[clazz]
                    ?: error("$clazz not part of this configuration schema")
        }
    }
}
