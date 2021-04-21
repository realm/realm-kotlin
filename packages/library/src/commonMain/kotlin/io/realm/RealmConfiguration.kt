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
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlin.reflect.KClass

/**
 * Configuration for log events created by a Realm instance.
 */
data class LogConfiguration(
        /**
         * The [LogLevel] for which all log events of equal or higher priority will be reported.
         */
        val level: LogLevel,
        /**
         * If `true`, the system logger is removed. Log events will only be reported if a [LogConfiguration.customLoggers]
         * is configured.
         */
        val removeSystemLogger: Boolean,
        /**
         * Any custom loggers to install. They will receive all log events with a priority equal to or higher than
         * the value defined in [LogConfiguration.level].
         */
        val customLoggers: List<RealmLogger>)


public class RealmConfiguration private constructor(
        companionMap: Map<KClass<*>, RealmObjectCompanion>,
        path: String?,
        name: String,
        schema: Set<KClass<out RealmObject>>,
        logConfig: LogConfiguration
) {

    // Public properties making up the RealmConfiguration
    public val path: String
    public val name: String

    public val schema: Set<KClass<out RealmObject>>
    public val log: LogConfiguration

    // Internal properties used by other Realm components, but does not make sense for the end user to know about
    internal var mapOfKClassWithCompanion: Map<KClass<*>, RealmObjectCompanion> = emptyMap()
    internal val nativeConfig: NativePointer = RealmInterop.realm_config_new()
    internal lateinit var mediator: Mediator

    init {
        this.path = if (path == null || path.isEmpty()) {
            val directory = PlatformHelper.appFilesDirectory()
            // FIXME Proper platform agnostic file separator: File.separator is not available for Kotlin/Native
            //  https://github.com/realm/realm-kotlin/issues/75
            "$directory/$name"
        } else path
        this.name = name // FIXME: Should read name from end of path
        this.schema = schema
        this.mapOfKClassWithCompanion = companionMap
        this.log = logConfig
        init()
    }

    /**
     * Short-hand for creating common variants of RealmConfigurations.
     *
     * @param path full path to the Realm file. If set, [RealmConfiguration.name] is ignored.
     * @param name name of the Realm file being created if no [RealmConfiguration.path] is configured. Realm files are
     *             placed in the default location for the platform. On Android this is in `getFilesDir()`
     * @param schema set of classes that make up the schema for the Realm. Identified by their class literal `T::class`.
     */
    public constructor(path: String? = null, name: String = Realm.DEFAULT_FILE_NAME, schema: Set<KClass<out RealmObject>> = setOf())
            : this(
            mapOf(),
            path,
            name,
            schema,
            LogConfiguration(LogLevel.WARN, false, listOf())) {
    }

    /**
     * Used to create a [RealmConfiguration]. For common use cases, a [RealmConfiguration] can be created directly
     * using the [RealmConfiguration] constructor.
     */
    class Builder(
            var path: String? = null, // Full path for Realm (directory + name)
            var name: String = Realm.DEFAULT_FILE_NAME, // Optional Realm name (default is 'default')
            vararg var schema: KClass<out RealmObject>
    ) {

        private var logLevel: LogLevel = LogLevel.WARN
        private var removeSystemLogger: Boolean = false
        private var customLoggers: List<RealmLogger> = listOf()

        fun path(path: String) = apply { this.path = path }
        fun name(name: String) = apply { this.name = name }
        fun schema(vararg classes: KClass<out RealmObject>) = apply { this.schema = classes }

        /**
         * Configure the [LogLevel] for which all log events of equal or higher priority will be reported.
         *
         * @param level Minimum log level to report.
         */
        fun logLevel(level: LogLevel) = apply { this.logLevel = level }

        /**
         * Removes the default system logger from being installed. If no [RealmConfiguration.Builder.customLoggers] have
         * been configured, not log events will be reported, regardless of the configured [RealmConfiguration.Builder.logLevel].
         */
        fun removeSystemLogger() = apply { this.removeSystemLogger = true }

        /**
         * Install custom loggers to handle log events. Only log events at equal or higher priority than [RealmConfiguration.Builder.logLevel]
         * will be sent the the loggers. A default system logger is installed by default that will redirect to the common
         * logging framework on the platform, i.e. LogCat on Android and NSLog on iOS.
         */
        fun customLoggers(loggers: List<RealmLogger>) = apply { this.customLoggers = loggers }

        fun build(): RealmConfiguration {
            @Suppress("SpreadOperator")
            return RealmConfiguration(path, name, setOf(*schema))
        }

        // Called from the compiler plugin
        internal fun build(companionMap: Map<KClass<*>, RealmObjectCompanion>): RealmConfiguration {
            return RealmConfiguration(companionMap,
                    path,
                    name,
                    setOf(*schema),
                    LogConfiguration(logLevel, removeSystemLogger, customLoggers))
        }
    }

    private fun init() {
        RealmInterop.realm_config_set_path(nativeConfig, path)
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
