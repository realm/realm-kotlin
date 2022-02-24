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

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

/**
 * This interface is used to determine if a Realm file should be compacted the first time the file
 * is opened and before the instance is returned.
 *
 * Note that compacting a file can take a while, so compacting should generally only be done as
 * part of opening a Realm on a background thread.
 */
fun interface CompactOnLaunchCallback {

    /**
     * This method determines if the Realm file should be compacted before opened and returned to
     * the user.
     *
     * @param totalBytes the total file size (data + free space).
     * @param usedBytes the total bytes used by data in the file.
     * @return `true` to indicate an attempt to compact the file should be made. Otherwise,
     * compaction will be skipped.
     */
    fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean
}

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

interface Configuration {
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
     * 64 byte key used to encrypt and decrypt the Realm file.
     *
     * @return null on unencrypted Realms.
     */
    val encryptionKey: ByteArray?

    /**
     * Callback that determines if the realm file should be compacted as part of opening it.
     *
     * @return `null` if the realm file should not be compacted when opened. Otherwise, the callback
     * returned is the one that will be invoked in order to determine if the file should be
     * compacted or not.
     * @see [RealmConfiguration.Builder.compactOnLaunch]
     */
    val compactOnLaunchCallback: CompactOnLaunchCallback?

    /**
     * Base class for configuration builders that holds properties available to both
     * [RealmConfiguration] and [SyncConfiguration].
     *
     * @param T the type of [Configuration] the builder should generate.
     * @param S the type of builder, needed to distinguish between local and sync variants.
     */
    // The property functions in this builder return the type of the builder itself, represented by
    // [S]. This is due to `library-base` not having visibility over `library-sync` and therefore
    // all function return types have to be typecast as [S].
    @Suppress("UnnecessaryAbstractClass", "UNCHECKED_CAST") // Actual implementations should rewire build() to companion map variant
    abstract class SharedBuilder<T, S : SharedBuilder<T, S>>(
        var schema: Set<KClass<out RealmObject>> = setOf()
    ) {
        protected var path: String? = null
        protected var name: String = Realm.DEFAULT_FILE_NAME
        protected var logLevel: LogLevel = LogLevel.WARN
        protected var removeSystemLogger: Boolean = false
        protected var userLoggers: List<RealmLogger> = listOf()
        protected var maxNumberOfActiveVersions: Long = Long.MAX_VALUE
        protected var notificationDispatcher: CoroutineDispatcher? = null
        protected var writeDispatcher: CoroutineDispatcher? = null
        protected var deleteRealmIfMigrationNeeded: Boolean = false
        protected var schemaVersion: Long = 0
        protected var encryptionKey: ByteArray? = null
        protected var compactOnLaunchCallback: CompactOnLaunchCallback? = null
        protected var migration: RealmMigration? = null

        /**
         * Creates the RealmConfiguration based on the builder properties.
         *
         * @return the created RealmConfiguration.
         */
        abstract fun build(): T

        /**
         * Sets the absolute path of the realm file.
         *
         * If not set the realm will be stored at the default app storage location for the platform.
         *
         * @param path either the canonical absolute path or a relative path from the current directory ('./').
         * ```
         * // For JVM platforms the current directory is obtained using
         *  System.getProperty("user.dir")
         *
         * // For macOS the current directory is obtained using
         * platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
         *
         * // For iOS the current directory is obtained using
         * NSFileManager.defaultManager.URLForDirectory(
         *      NSDocumentDirectory,
         *      NSUserDomainMask,
         *      null,
         *      true,
         *      null
         * )
         * ```
         */
        fun path(path: String?): S = apply { this.path = path } as S

        /**
         * Sets the filename of the realm file.
         *
         * If setting the full path of the realm, this name is not taken into account.
         */
        fun name(name: String) = apply { this.name = name } as S

        /**
         * Sets the classes of the schema.
         *
         * The elements of the set must be direct class literals.
         *
         * @param classes the set of classes that the schema consists of.
         */
        fun schema(classes: Set<KClass<out RealmObject>>) = apply { this.schema = classes } as S

        /**
         * Sets the classes of the schema.
         *
         * The `classes` arguments must be direct class literals.
         *
         * @param classes the classes that the schema consists of.
         */
        fun schema(vararg classes: KClass<out RealmObject>) =
            apply { this.schema = setOf(*classes) } as S

        /**
         * Sets the migration to handle schema updates.
         *
         * @param migration the [RealmMigration] instance to handle schema and data migration in the
         * event of a schema update.
         *
         * @see RealmMigration
         * @see AutomaticSchemaMigration
         */
        fun migration(migration: RealmMigration): S = apply { this.migration = migration } as S

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
         * For details see the *Large Realm file size*-section of the
         * [FAQ](https://docs.mongodb.com/realm-legacy/docs/java/latest/index.html#faq)
         *
         * @param number the maximum number of active versions before an exception is thrown.
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
        open fun log(
            level: LogLevel = LogLevel.WARN,
            customLoggers: List<RealmLogger> = emptyList()
        ) = apply {
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
         * @param dispatcher dispatcher on which writes are run. It is required to be backed by a
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
         * Sets the schema version of the Realm. This must be equal to or higher than the schema version of the existing
         * Realm file, if any. If the schema version is higher than the already existing Realm, a migration is needed.
         */
        fun schemaVersion(schemaVersion: Long): S {
            if (schemaVersion < 0) {
                throw IllegalArgumentException("Realm schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion")
            }
            return apply { this.schemaVersion = schemaVersion } as S
        }

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
         * Sets a callback for controlling whether the realm should be compacted when opened.
         *
         * Due to the way Realm allocates space on disk, it is sometimes the case that more space
         * is allocated than what is actually needed, making the realm file larger than what it
         * needs to be. This mostly occurs when writing larger binary blobs to the file.
         *
         * The space will be used by subsequent writes, but in the interim period the file will
         * be larger than what is strictly needed.
         *
         * This method makes it possible to define a function that determines whether or not
         * the file should be compacted when the realm is opened, optimizing how much disk size
         * is used.
         *
         * @param callback The callback called when opening the realm file. The return value
         * determines whether or not the file should be compacted. If not user defined callback
         * is defined, the default callback will be used. See [Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK]
         * for more details.
         */
        fun compactOnLaunch(callback: CompactOnLaunchCallback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK) =
            apply { this.compactOnLaunchCallback = callback } as S

        /**
         * Removes the default system logger from being installed. If no custom loggers have
         * been configured, no log events will be reported, regardless of the configured
         * log level.
         *
         * @see [Configuration.Builder.log]
         */
        // TODO Evaluate if this should be part of the public API. For now keep it internal.
        internal fun removeSystemLogger() = apply { this.removeSystemLogger = true } as S

        protected fun validateEncryptionKey(encryptionKey: ByteArray): ByteArray {
            if (encryptionKey.size != Realm.ENCRYPTION_KEY_LENGTH) {
                throw IllegalArgumentException("The provided key must be ${Realm.ENCRYPTION_KEY_LENGTH} bytes. The provided key was ${encryptionKey.size} bytes.")
            }
            return encryptionKey
        }
    }
}
