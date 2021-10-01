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

import io.realm.internal.REPLACED_BY_IR
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * A _Realm Configuration_ defining specific setup and configuration for a Realm instance.
 *
 * The RealmConfiguration can, for simple uses cases, be created directly through the constructor.
 * More advanced setup requires building the RealmConfiguration through
 * [RealmConfiguration.Builder.build].
 *
 * @see Realm
 * @see RealmConfiguration.Builder
 */
interface RealmConfiguration {
    // Public properties making up the RealmConfiguration
    // TODO Add more elaborate KDoc for all of these
    /**
     * Path to the realm file.
     */
    public val path: String

    /**
     * Filename of the realm file.
     */
    public val name: String

    /**
     * The set of classes included in the schema for the realm.
     */
    public val schema: Set<KClass<out RealmObject>>

    /**
     * The log configuration used for the realm instance.
     */
    public val log: LogConfiguration

    /**
     * Maximum number of active versions.
     *
     * Holding references to objects from previous version of the data in the realm will also
     * require keeping the data in the actual file. This can cause growth of the file. See
     * [Builder.maxNumberOfActiveVersions] for details.
     */
    public val maxNumberOfActiveVersions: Long

    /**
     * The schema version.
     */
    public val schemaVersion: Long

    /**
     * Flag indicating whether the realm will be deleted if the schema has changed in a way that
     * requires schema migration.
     */
    public val deleteRealmIfMigrationNeeded: Boolean

    /**
     * 64 byte key used to encrypt and decrypt the Realm file.
     *
     * @return null on unencrypted Realms.
     */
    val encryptionKey: ByteArray?

    companion object {
        /**
         * Create a configuration using default values except for schema, path and name.
         *
         * @param path The full path of the realm file.
         * @param name The filename of the realm file.
         * @param schema The classes of the schema. The elements of the set must be direct class literals.
         */
        // Should always follow Builder constructor arguments
        fun with(
            path: String? = null,
            name: String = Realm.DEFAULT_FILE_NAME,
            schema: Set<KClass<out RealmObject>>
        ): RealmConfiguration {
            REPLACED_BY_IR() // Will be replace by Builder(path, name, schame).build(companionMap)
        }
    }

    /**
     * TODO
     */
    abstract class SharedBuilder<S>(
        var path: String? = null, // Full path for Realm (directory + name)
        var name: String = Realm.DEFAULT_FILE_NAME, // Optional Realm name (default is 'default')
        var schema: Set<KClass<out RealmObject>> = setOf()
    ) {

        protected var logLevel: LogLevel = LogLevel.WARN
        protected var removeSystemLogger: Boolean = false
        protected var userLoggers: List<RealmLogger> = listOf()
        protected var maxNumberOfActiveVersions: Long = Long.MAX_VALUE
        protected var notificationDispatcher: CoroutineDispatcher? = null
        protected var writeDispatcher: CoroutineDispatcher? = null
        protected var deleteRealmIfMigrationNeeded: Boolean = false
        protected var schemaVersion: Long = 0
        protected var encryptionKey: ByteArray? = null

        /**
         * Creates the RealmConfiguration based on the builder properties.
         *
         * @return the created RealmConfiguration.
         */
//        abstract fun build(): RealmConfiguration

        // Called from the compiler plugin
        // FIXME children should implement this without defining it here so that it is internal
        //  regardless. Handle bypass between build() and this function via IR
//        abstract fun build(companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>): RealmConfiguration

        /**
         * Sets the absolute path of the realm file.
         */
        fun path(path: String): S = apply { this.path = path } as S

        /**
         * Sets the filename of the realm file.
         */
        fun name(name: String) = apply { this.name = name } as S

        /**
         * Sets the classes of the schema.
         *
         * The elements of the set must be direct class literals.
         *
         * @param classes The set of classes that the schema consists of.
         */
        fun schema(classes: Set<KClass<out RealmObject>>) = apply { this.schema = classes } as S

        /**
         * Sets the classes of the schema.
         *
         * The `classes` arguments must be direct class literals.
         *
         * @param classes The classes that the schema consists of.
         */
        fun schema(vararg classes: KClass<out RealmObject>) =
            apply { this.schema = setOf(*classes) } as S

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
        } as S

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
            } as S

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
        internal fun notificationDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.notificationDispatcher = dispatcher
        } as S

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
        internal fun writeDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.writeDispatcher = dispatcher
        } as S

        /**
         * Setting this will change the behavior of how migration exceptions are handled. Instead of throwing an
         * exception the on-disc Realm will be cleared and recreated with the new Realm schema.
         *
         * **WARNING!** This will result in loss of data.
         */
        fun deleteRealmIfMigrationNeeded() = apply { this.deleteRealmIfMigrationNeeded = true } as S

        /**
         * Sets the schema version of the Realm. This must be equal to or higher than the schema version of the existing
         * Realm file, if any. If the schema version is higher than the already existing Realm, a migration is needed.
         */
        fun schemaVersion(schemaVersion: Long) =
            apply { this.schemaVersion = validateSchemaVersion(schemaVersion) } as S

        /**
         * Sets the 64 byte key used to encrypt and decrypt the Realm file. If no key is provided the Realm file
         * will be unencrypted.
         *
         * It is important that this key is created and stored securely. See [this link](https://docs.mongodb.com/realm/sdk/android/advanced-guides/encryption/) for suggestions on how to do that.
         *
         * @param encryptionKey 64-byte encryption key.
         */
        fun encryptionKey(encryptionKey: ByteArray) =
            apply { this.encryptionKey = validateEncryptionKey(encryptionKey) } as S

        /**
         * TODO Evaluate if this should be part of the public API. For now keep it internal.
         *
         * Removes the default system logger from being installed. If no custom loggers have
         * been configured, no log events will be reported, regardless of the configured
         * log level.
         *
         * @see [RealmConfiguration.Builder.log]
         */
        internal fun removeSystemLogger() = apply { this.removeSystemLogger = true } as S

        protected fun validateSchemaVersion(schemaVersion: Long): Long {
            if (schemaVersion < 0) {
                throw IllegalArgumentException("Realm schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion")
            }
            return schemaVersion
        }

        protected fun validateEncryptionKey(encryptionKey: ByteArray): ByteArray {
            if (encryptionKey.size != Realm.ENCRYPTION_KEY_LENGTH) {
                throw IllegalArgumentException("The provided key must be ${Realm.ENCRYPTION_KEY_LENGTH} bytes. The provided key was ${encryptionKey.size} bytes.")
            }
            return encryptionKey
        }
    }

    /**
     * Used to create a [RealmConfiguration]. For common use cases, a [RealmConfiguration] can be created directly
     * using the [RealmConfiguration] constructor.
     */
    // TODO so far this is the least-effort implementation for supporting sync configurations too
    //  though interfacing the builder is also an option
    class Builder(
        path: String? = null, // Full path for Realm (directory + name)
        name: String = Realm.DEFAULT_FILE_NAME, // Optional Realm name (default is 'default')
        schema: Set<KClass<out RealmObject>> = setOf()
    ) : SharedBuilder<Builder>(path, name, schema) {

        /**
         * Creates the RealmConfiguration based on the builder properties.
         *
         * @return the created RealmConfiguration.
         */
        fun build(): RealmConfiguration {
            REPLACED_BY_IR()
        }

        // Called from the compiler plugin
        fun build(
            companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>
        ): RealmConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)
            return RealmConfigurationImpl(
                companionMap,
                path,
                name,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(name),
                writeDispatcher ?: singleThreadDispatcher(name),
                schemaVersion,
                deleteRealmIfMigrationNeeded,
                encryptionKey
            )
        }
    }
}
