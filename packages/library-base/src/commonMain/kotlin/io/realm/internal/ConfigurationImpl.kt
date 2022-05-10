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

import io.realm.BaseRealmObject
import io.realm.CompactOnLaunchCallback
import io.realm.LogConfiguration
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealm
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.internal.dynamic.DynamicMutableRealmImpl
import io.realm.internal.dynamic.DynamicMutableRealmObjectImpl
import io.realm.internal.dynamic.DynamicRealmImpl
import io.realm.internal.dynamic.DynamicRealmObjectImpl
import io.realm.internal.interop.FrozenRealmPointer
import io.realm.internal.interop.LiveRealmPointer
import io.realm.internal.interop.RealmConfigurationPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSchemaPointer
import io.realm.internal.interop.SchemaMode
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.prepareRealmFilePath
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.migration.AutomaticSchemaMigration
import io.realm.migration.RealmMigration
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

// TODO Public due to being accessed from `library-sync`
@Suppress("LongParameterList")
public open class ConfigurationImpl constructor(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseRealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcher,
    writeDispatcher: CoroutineDispatcher,
    schemaVersion: Long,
    schemaMode: SchemaMode,
    encryptionKey: ByteArray?,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    migration: RealmMigration?,
) : InternalConfiguration {

    override val path: String

    override val name: String

    override val schema: Set<KClass<out BaseRealmObject>>

    override val log: LogConfiguration

    override val maxNumberOfActiveVersions: Long

    override val schemaVersion: Long

    override val schemaMode: SchemaMode

    override val encryptionKey: ByteArray?
        get(): ByteArray? = RealmInterop.realm_config_get_encryption_key(nativeConfig)

    override val mapOfKClassWithCompanion: Map<KClass<out BaseRealmObject>, RealmObjectCompanion>

    override val mediator: Mediator

    override val nativeConfig: RealmConfigurationPointer = RealmInterop.realm_config_new()

    override val notificationDispatcher: CoroutineDispatcher

    override val writeDispatcher: CoroutineDispatcher

    override val compactOnLaunchCallback: CompactOnLaunchCallback?

    init {
        this.path = normalizePath(directory, name)
        this.name = name
        this.schema = schema
        this.mapOfKClassWithCompanion = schema.associateWith { realmObjectCompanionOrThrow(it) }
        this.log = logConfig
        this.maxNumberOfActiveVersions = maxNumberOfActiveVersions
        this.notificationDispatcher = notificationDispatcher
        this.writeDispatcher = writeDispatcher
        this.schemaVersion = schemaVersion
        this.schemaMode = schemaMode
        this.compactOnLaunchCallback = compactOnLaunchCallback

        RealmInterop.realm_config_set_path(nativeConfig, this.path)
        RealmInterop.realm_config_set_schema_mode(nativeConfig, schemaMode)
        RealmInterop.realm_config_set_schema_version(config = nativeConfig, version = schemaVersion)
        compactOnLaunchCallback?.let { callback ->
            RealmInterop.realm_config_set_should_compact_on_launch_function(
                nativeConfig,
                object : io.realm.internal.interop.CompactOnLaunchCallback {
                    override fun invoke(totalBytes: Long, usedBytes: Long): Boolean {
                        return callback.shouldCompact(totalBytes, usedBytes)
                    }
                }
            )
        }

        val nativeSchema = RealmInterop.realm_schema_new(
            mapOfKClassWithCompanion.values.map { it ->
                it.`io_realm_kotlin_schema`().let { it.cinteropClass to it.cinteropProperties }
            }
        )

        RealmInterop.realm_config_set_schema(nativeConfig, nativeSchema)
        RealmInterop.realm_config_set_max_number_of_active_versions(
            nativeConfig,
            maxNumberOfActiveVersions
        )

        migration?.let {
            when (it) {
                is AutomaticSchemaMigration ->
                    RealmInterop.realm_config_set_migration_function(nativeConfig) { oldRealm: FrozenRealmPointer, newRealm: LiveRealmPointer, schema: RealmSchemaPointer ->
                        // If we don't start a read, then we cannot read the version
                        RealmInterop.realm_begin_read(oldRealm)
                        RealmInterop.realm_begin_read(newRealm)
                        val old = DynamicRealmImpl(this@ConfigurationImpl, oldRealm)
                        val new = DynamicMutableRealmImpl(this@ConfigurationImpl, newRealm)
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            it.migrate(object : AutomaticSchemaMigration.MigrationContext {
                                override val oldRealm: DynamicRealm = old
                                override val newRealm: DynamicMutableRealm = new
                            })
                            true
                        } catch (e: Throwable) {
                            // Returning false will cause Realm.open to fail with a
                            // RuntimeException with a text saying "User-provided callback failed"
                            // which is the closest that we can get across platforms, so dump the
                            // actual exception to stdout, so users have a chance to see what is
                            // actually failing
                            // TODO Should we dump the actual exceptions in a platform specific way
                            //  https://github.com/realm/realm-kotlin/issues/665
                            e.printStackTrace()
                            false
                        }
                    }
            }
            Unit
        }

        encryptionKey?.let {
            RealmInterop.realm_config_set_encryption_key(nativeConfig, it)
        }

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<out BaseRealmObject>): RealmObjectInternal =
                when (clazz) {
                    DynamicRealmObject::class -> DynamicRealmObjectImpl()
                    DynamicMutableRealmObject::class -> DynamicMutableRealmObjectImpl()
                    DynamicUnmanagedRealmObject::class -> DynamicMutableRealmObjectImpl()
                    else ->
                        companionOf(clazz).`io_realm_kotlin_newInstance`() as RealmObjectInternal
                }

            override fun companionOf(clazz: KClass<out BaseRealmObject>): RealmObjectCompanion =
                mapOfKClassWithCompanion[clazz]
                    ?: error("$clazz not part of this configuration schema")
        }
    }

    // TODO Verify that this logic works on Windows?
    // FIXME See https://github.com/realm/realm-kotlin/issues/699
    private fun normalizePath(directoryPath: String, fileName: String): String {
        var dir = directoryPath.ifEmpty { appFilesDirectory() }
        // If dir is a relative path, replace with full path for easier debugging
        if (dir.startsWith("./")) {
            dir = dir.replaceFirst("./", "${appFilesDirectory()}/")
        }
        return prepareRealmFilePath(dir, fileName)
    }
}
