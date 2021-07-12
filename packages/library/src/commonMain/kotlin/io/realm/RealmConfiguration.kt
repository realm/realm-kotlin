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

/**
 * Configuration for log events created by a Realm instance.
 */
// FIXME Any reason for this to be public?
public data class LogConfiguration(
    /**
     * The [LogLevel] for which all log events of equal or higher priority will be reported.
     */
    public val level: LogLevel,

    /**
     * Any loggers to install. They will receive all log events with a priority equal to or higher than
     * the value defined in [LogConfiguration.level].
     */
    public val loggers: List<RealmLogger>
)

/**
 * A _Realm Configuration_ defining specific setup and configuration for a Realm instance.
 *
 * The RealmConfiguration can, for simple uses cases, be created directly through the constructor,
 * while more advanced setup requires building the RealmConfiguration through
 * [RealmConfiguration.Builder.build].
 *
 * @see Realm.open
 * @see RealmConfiguration.Builder
 */
@Suppress("LongParameterList")
public class RealmConfiguration private constructor(
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
) {
    // Public properties making up the RealmConfiguration
    // TODO Add KDoc for all of these
    public val path: String
    public val name: String
    public val schema: Set<KClass<out RealmObject>>
    public val log: LogConfiguration
    public val maxNumberOfActiveVersions: Long
    public val notificationDispatcher: CoroutineDispatcher
    public val writeDispatcher: CoroutineDispatcher
    public val schemaVersion: Long
    public val deleteRealmIfMigrationNeeded: Boolean

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
        false
    )

    /**
     * Used to create a [RealmConfiguration]. For common use cases, a [RealmConfiguration] can be created directly
     * using the [RealmConfiguration] constructor.
     */
    class Builder(
        var path: String? = null, // Full path for Realm (directory + name)
        var name: String = Realm.DEFAULT_FILE_NAME, // Optional Realm name (default is 'default')
        var schema: Set<KClass<out RealmObject>> = setOf()
    ) {

        private var logLevel: LogLevel = LogLevel.WARN
        private var removeSystemLogger: Boolean = false
        private var userLoggers: List<RealmLogger> = listOf()
        private var maxNumberOfActiveVersions: Long = Long.MAX_VALUE
        private var notificationDispatcher: CoroutineDispatcher? = null
        private var writeDispatcher: CoroutineDispatcher? = null
        private var deleteRealmIfMigrationNeeded: Boolean = false
        private var schemaVersion: Long = 0

        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(classes: Set<KClass<out RealmObject>>) = apply { this.schema = classes }
        fun schema(vararg classes: KClass<out RealmObject>) =
            apply { this.schema = setOf(*classes) }

        /**
         * Sets the maximum number of live versions in the Realm file before an [IllegalStateException] is thrown when
         * attempting to write more data.
         *
         * Realm is capable of concurrently handling many different versions of Realm objects, this can e.g. happen if
         * a flow is slow to process data from the database while a fast writer is putting data into the Realm.
         *
         * Under normal circumstances this is not a problem, but if the number of active versions grow too large, it
         * will have a negative effect on the file size on disk. Setting this parameters can therefore be used to
         * prevent uses of Realm that can result in very large file sizes.
         *
         * @param number the maximum number of active versions before an exception is thrown.
         * @see [FAQ](https://realm.io/docs/java/latest/.faq-large-realm-file-size)
         */
        fun maxNumberOfActiveVersions(maxVersions: Long = 8) = apply {
            if (maxVersions < 1) {
                throw IllegalArgumentException("Only positive numbers above 0 are allowed. Yours was: $maxVersions")
            }
            this.maxNumberOfActiveVersions = maxVersions
        }

        /**
         * Configure how Realm will report log events.
         *
         * @param level all events at this level or higher will be reported.
         * @param customLoggers any custom loggers to send log events to. A default system logger is
         * installed by default that will redirect to the common logging framework on the platform, i.e.
         * LogCat on Android and NSLog on iOS.
         */
        fun log(level: LogLevel = LogLevel.WARN, customLoggers: List<RealmLogger> = emptyList()) =
            apply {
                this.logLevel = level
                this.userLoggers = customLoggers
            }

        /**
         * Dispatcher used to run background writes to the Realm.
         *
         * Defaults to a single threaded dispatcher started when the configuration is built.
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher Dispatcher on which writes are run. It is required to be backed by a
         * single thread only.
         */
        public fun notificationDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.notificationDispatcher = dispatcher
        }

        /**
         * Dispatcher on which Realm notifications are run. It is possible to listen for changes to
         * Realm objects from any thread, but the underlying logic will run on this dispatcher
         * before any changes are returned to the caller thread.
         *
         * Defaults to a single threaded dispatcher started when the configuration is built.
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher Dispatcher on which notifications are run. It is required to be backed
         * by a single thread only.
         */
        public fun writeDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.writeDispatcher = dispatcher
        }

        /**
         * Setting this will change the behavior of how migration exceptions are handled. Instead of throwing an
         * exception the on-disc Realm will be cleared and recreated with the new Realm schema.
         *
         * **WARNING!** This will result in loss of data.
         */
        fun deleteRealmIfMigrationNeeded() = apply { this.deleteRealmIfMigrationNeeded = true }

        /**
         * Sets the schema version of the Realm. This must be equal to or higher than the schema version of the existing
         * Realm file, if any. If the schema version is higher than the already existing Realm, a migration is needed.
         */
        fun schemaVersion(schemaVersion: Long) =
            apply { this.schemaVersion = validateSchemaVersion(schemaVersion) }

        /**
         * TODO Evaluate if this should be part of the public API. For now keep it internal.
         *
         * Removes the default system logger from being installed. If no custom loggers have
         * been configured, no log events will be reported, regardless of the configured
         * log level.
         *
         * @see [RealmConfiguration.Builder.log]
         */
        internal fun removeSystemLogger() = apply { this.removeSystemLogger = true }

        fun build(): RealmConfiguration {
            REPLACED_BY_IR()
        }

        // Called from the compiler plugin
        internal fun build(companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>): RealmConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(PlatformHelper.createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)
            return RealmConfiguration(
                companionMap,
                path,
                name,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(name),
                writeDispatcher ?: singleThreadDispatcher(name),
                schemaVersion,
                deleteRealmIfMigrationNeeded
            )
        }

        private fun validateSchemaVersion(schemaVersion: Long): Long {
            if (schemaVersion < 0) {
                throw IllegalArgumentException("Realm schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion")
            }
            return schemaVersion
        }
    }
}
