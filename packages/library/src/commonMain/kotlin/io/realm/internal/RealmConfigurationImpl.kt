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
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.internal.Mediator
import io.realm.internal.PlatformHelper
import io.realm.internal.REPLACED_BY_IR
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.RealmObjectInternal
import io.realm.internal.singleThreadDispatcher
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
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
    // Public properties making up the RealmConfiguration
    // TODO Add more elaborate KDoc for all of these
    /**
     * Path to the realm file.
     */
    override val path: String

    /**
     * Filename of the realm file.
     */
    override val name: String

    /**
     * The set of classes included in the schema for the realm.
     */
    override val schema: Set<KClass<out RealmObject>>

    /**
     * The log configuration used for the realm instance.
     */
    override val log: LogConfiguration

    /**
     * Maximum number of active versions.
     *
     * Holding references to objects from previous version of the data in the realm will also
     * require keeping the data in the actual file. This can cause growth of the file. See
     * [Builder.maxNumberOfActiveVersions] for details.
     */
    override val maxNumberOfActiveVersions: Long

    /**
     * The coroutine dispatcher for internal handling of notification registration and delivery.
     */
    override val notificationDispatcher: CoroutineDispatcher

    /**
     * The coroutine dispatcher used for all write operations.
     */
    override val writeDispatcher: CoroutineDispatcher

    /**
     * The schema version.
     */
    override val schemaVersion: Long

    /**
     * Flag indicating whether the realm will be deleted if the schema has changed in a way that
     * requires schema migration.
     */
    override val deleteRealmIfMigrationNeeded: Boolean

    /**
     * 64 byte key used to encrypt and decrypt the Realm file.
     *
     * @return null on unencrypted Realms.
     */
    override val encryptionKey get(): ByteArray? = RealmInterop.realm_config_get_encryption_key(nativeConfig)

    // Internal properties used by other Realm components, but does not make sense for the end user to know about
    internal var mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>
    internal var mediator: Mediator

    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()

    init {
        this.path = if (path == null || path.isEmpty()) {
            val directory = PlatformHelper.appFilesDirectory()
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

    /**
     * Short-hand for creating common variants of RealmConfigurations.
     *
     * @param path full path to the Realm file. If set, [RealmConfiguration.name] is ignored.
     * @param name name of the Realm file being created if no [RealmConfiguration.path] is configured. Realm files are
     *             placed in the default location for the platform. On Android this is in `getFilesDir()`
     * @param schema set of classes that make up the schema for the Realm. Identified by their class literal `T::class`.
     */
    // This constructor is never used at runtime, all calls to it are being rewired by the Realm Compiler Plugin to call
    // the internal secondary constructor with all schema classes mapped to their RealmCompanion.
    public constructor(
        path: String? = null,
        name: String = Realm.DEFAULT_FILE_NAME,
        schema: Set<KClass<out RealmObject>>
    ) : this(path, name, mapOf()) // REPLACED_BY_IR

    // Called by the compiler plugin, with a populated companion map.
    // Default values should match what happens when calling `RealmConfiguration.Builder(schema = setOf(...)).build()`
    internal constructor(
        path: String? = null,
        name: String = Realm.DEFAULT_FILE_NAME,
        schema: Map<KClass<out RealmObject>, RealmObjectCompanion>
    ) : this(
        schema,
        path,
        name,
        schema.keys,
        LogConfiguration(
            LogLevel.WARN,
            listOf(PlatformHelper.createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
        ),
        Long.MAX_VALUE,
        singleThreadDispatcher(name),
        singleThreadDispatcher(name),
        0,
        false,
        null,
    )

}
