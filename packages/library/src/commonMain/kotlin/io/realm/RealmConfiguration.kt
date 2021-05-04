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
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.SchemaMode
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlin.reflect.KClass

/**
 * Configuration for log events created by a Realm instance.
 */
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

public class RealmConfiguration private constructor(
    companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>,
    path: String?,
    name: String,
    schema: Set<KClass<out RealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long
) {

    // Public properties making up the RealmConfiguration
    // TODO Add KDoc for all of these
    public val path: String
    public val name: String
    public val schema: Set<KClass<out RealmObject>>
    public val log: LogConfiguration
    public val maxNumberOfActiveVersions: Long

    // Internal properties used by other Realm components, but does not make sense for the end user to know about
    internal var mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>
    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()
    internal lateinit var mediator: Mediator

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

        RealmInterop.realm_config_set_path(nativeConfig, this.path)
        RealmInterop.realm_config_set_schema_mode(
            nativeConfig,
            SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
        )
        RealmInterop.realm_config_set_schema_version(nativeConfig, version = 0) // TODO expose version when handling migration modes
        val schema = RealmInterop.realm_schema_new(mapOfKClassWithCompanion.values.map { it.`$realm$schema`() })
        RealmInterop.realm_config_set_schema(nativeConfig, schema)
        RealmInterop.realm_config_set_max_number_of_active_versions(nativeConfig, maxNumberOfActiveVersions)

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<*>): RealmObjectInternal = (
                mapOfKClassWithCompanion[clazz]?.`$realm$newInstance`()
                    ?: error("$clazz not part of this configuration schema")
                ) as RealmObjectInternal

            override fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion = mapOfKClassWithCompanion[clazz]
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
    public constructor(path: String? = null, name: String = Realm.DEFAULT_FILE_NAME, schema: Set<KClass<out RealmObject>>) :
        this(path, name, mapOf())

    // Called by the compiler plugin, with a populated companion map
    internal constructor(path: String? = null, name: String = Realm.DEFAULT_FILE_NAME, schema: Map<KClass<out RealmObject>, RealmObjectCompanion>) :
        this(
            schema,
            path,
            name,
            schema.keys,
            LogConfiguration(LogLevel.WARN, listOf(PlatformHelper.createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))),
            Long.MAX_VALUE
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

        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(classes: Set<KClass<out RealmObject>>) = apply { this.schema = classes }
        fun schema(vararg classes: KClass<out RealmObject>) = apply { this.schema = setOf(*classes) }

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
        fun log(level: LogLevel = LogLevel.WARN, customLoggers: List<RealmLogger> = emptyList()) = apply {
            this.logLevel = level
            this.userLoggers = customLoggers
        }

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
                maxNumberOfActiveVersions
            )
        }
    }
}
