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

package io.realm.kotlin.internal

import io.realm.kotlin.CompactOnLaunchCallback
import io.realm.kotlin.InitialDataCallback
import io.realm.kotlin.InitialRealmFileConfiguration
import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.dynamic.DynamicMutableRealmImpl
import io.realm.kotlin.internal.dynamic.DynamicMutableRealmObjectImpl
import io.realm.kotlin.internal.dynamic.DynamicRealmImpl
import io.realm.kotlin.internal.dynamic.DynamicRealmObjectImpl
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.FrozenRealmPointer
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.interop.MigrationCallback
import io.realm.kotlin.internal.interop.RealmConfigurationPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSchemaPointer
import io.realm.kotlin.internal.interop.SchemaMode
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.prepareRealmFilePath
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.util.CoroutineRealmSchedulerFactory
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.migration.RealmMigration
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass

// TODO Public due to being accessed from `library-sync`
@Suppress("LongParameterList")
public open class ConfigurationImpl constructor(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseRealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineRealmSchedulerFactory,
    writeDispatcher: CoroutineRealmSchedulerFactory,
    schemaVersion: Long,
    schemaMode: SchemaMode,
    private val userEncryptionKey: ByteArray?,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    private val userMigration: RealmMigration?,
    initialDataCallback: InitialDataCallback?,
    override val isFlexibleSyncConfiguration: Boolean,
    inMemory: Boolean,
    initialRealmFileConfiguration: InitialRealmFileConfiguration?,
    logger: ContextLogger
) : InternalConfiguration {

    override val path: String

    override val name: String

    override val schema: Set<KClass<out BaseRealmObject>>

    override val log: LogConfiguration

    override val maxNumberOfActiveVersions: Long

    override val schemaVersion: Long

    override val schemaMode: SchemaMode

    override val logger: ContextLogger = logger

    override val encryptionKey: ByteArray?
        get(): ByteArray? = userEncryptionKey

    override val mapOfKClassWithCompanion: Map<KClass<out BaseRealmObject>, RealmObjectCompanion>

    override val mediator: Mediator

    override val notificationDispatcherFactory: CoroutineRealmSchedulerFactory

    override val writeDispatcherFactory: CoroutineRealmSchedulerFactory

    override val compactOnLaunchCallback: CompactOnLaunchCallback?

    override val initialDataCallback: InitialDataCallback?
    override val inMemory: Boolean
    override val initialRealmFileConfiguration: InitialRealmFileConfiguration?

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val nativeConfig: RealmConfigurationPointer = RealmInterop.realm_config_new()
        return configInitializer(nativeConfig)
    }

    override suspend fun openRealm(realm: RealmImpl): Pair<FrozenRealmReference, Boolean> {
        val configPtr = realm.configuration.createNativeConfiguration()
        val (dbPointer, fileCreated) = RealmInterop.realm_open(configPtr)
        val liveRealmReference = LiveRealmReference(realm, dbPointer)
        val frozenReference = liveRealmReference.snapshot(realm)
        liveRealmReference.close()
        return frozenReference to fileCreated
    }

    override suspend fun initializeRealmData(realm: RealmImpl, realmFileCreated: Boolean) {
        val initCallback = initialDataCallback
        if (realmFileCreated && initCallback != null) {
            realm.write { // this: MutableRealm
                with(initCallback) { // this: InitialDataCallback
                    write()
                }
            }
        }
    }

    private val configInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    init {
        this.path = normalizePath(directory, name)
        this.name = name
        this.schema = schema
        this.mapOfKClassWithCompanion = schema.associateWith { realmObjectCompanionOrThrow(it) }
        this.log = logConfig
        this.maxNumberOfActiveVersions = maxNumberOfActiveVersions
        this.notificationDispatcherFactory = notificationDispatcher
        this.writeDispatcherFactory = writeDispatcher
        this.schemaVersion = schemaVersion
        this.schemaMode = schemaMode
        this.compactOnLaunchCallback = compactOnLaunchCallback
        this.initialDataCallback = initialDataCallback
        this.inMemory = inMemory
        this.initialRealmFileConfiguration = initialRealmFileConfiguration

        // We need to freeze `compactOnLaunchCallback` reference on initial thread for Kotlin Native
        val compactCallback = compactOnLaunchCallback?.let { callback ->
            object : io.realm.kotlin.internal.interop.CompactOnLaunchCallback {
                override fun invoke(totalBytes: Long, usedBytes: Long): Boolean {
                    return callback.shouldCompact(totalBytes, usedBytes)
                }
            }
        }

        // We need to prepare the the migration callback so it can be frozen for Kotlin Native, but
        // we cannot freeze it until it is actually used since it has a reference to this
        // ConfigurationImpl,so freezing it now would make further initialization impossible.
        val migrationCallback: MigrationCallback? = userMigration?.let { userMigration ->
            when (userMigration) {
                is AutomaticSchemaMigration -> MigrationCallback { oldRealm: FrozenRealmPointer, newRealm: LiveRealmPointer, schema: RealmSchemaPointer ->
                    // If we don't start a read, then we cannot read the version
                    RealmInterop.realm_begin_read(oldRealm)
                    RealmInterop.realm_begin_read(newRealm)
                    val old = DynamicRealmImpl(this@ConfigurationImpl, oldRealm)
                    val new = DynamicMutableRealmImpl(this@ConfigurationImpl, newRealm)
                    userMigration.migrate(object : AutomaticSchemaMigration.MigrationContext {
                        override val oldRealm: DynamicRealm = old
                        override val newRealm: DynamicMutableRealm = new
                    })
                }
            }
        }

        // Verify schema invariants that cannot be captured at compile time nor by Core.
        // For now, the only invariant we capture here is wrong use of @PersistedName on classes
        // which might accidentally create multiple model classes with the same name.
        val duplicates: Set<String> = mapOfKClassWithCompanion.values
            .map { it.`io_realm_kotlin_schema`().name }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("The schema has declared the following class names multiple times: ${duplicates.joinToString()}")
        }

        // Invariant: All native modifications should happen inside this initializer, as that
        // wil allow us to construct multiple Config objects in Core that all can be used to open
        // the same Realm.
        this.configInitializer = { nativeConfig: RealmConfigurationPointer ->
            RealmInterop.realm_config_set_path(nativeConfig, this.path)
            RealmInterop.realm_config_set_schema_mode(nativeConfig, schemaMode)
            RealmInterop.realm_config_set_schema_version(config = nativeConfig, version = schemaVersion)

            compactCallback?.let { callback ->
                RealmInterop.realm_config_set_should_compact_on_launch_function(
                    nativeConfig,
                    callback
                )
            }

            val nativeSchema = RealmInterop.realm_schema_new(
                mapOfKClassWithCompanion.values.map {
                    it.`io_realm_kotlin_schema`().let {
                        // Core needs to process the properties in a particular order:
                        // first the real properties and then the computed ones
                        it.cinteropClass to it.cinteropProperties.sortedBy { it.isComputed }
                    }
                }
            )

            RealmInterop.realm_config_set_schema(nativeConfig, nativeSchema)
            RealmInterop.realm_config_set_max_number_of_active_versions(
                nativeConfig,
                maxNumberOfActiveVersions
            )

            migrationCallback?.let {
                RealmInterop.realm_config_set_migration_function(nativeConfig, it)
            }

            userEncryptionKey?.let { key: ByteArray ->
                RealmInterop.realm_config_set_encryption_key(nativeConfig, key)
            }

            RealmInterop.realm_config_set_in_memory(nativeConfig, inMemory)

            nativeConfig
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
