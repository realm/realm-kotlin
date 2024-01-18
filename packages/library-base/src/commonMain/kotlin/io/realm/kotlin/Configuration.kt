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

package io.realm.kotlin

import io.realm.kotlin.Configuration.SharedBuilder
import io.realm.kotlin.annotations.ExperimentalEncryptionCallbackApi
import io.realm.kotlin.internal.MISSING_PLUGIN_MESSAGE
import io.realm.kotlin.internal.REALM_FILE_EXTENSION
import io.realm.kotlin.internal.platform.PATH_SEPARATOR
import io.realm.kotlin.internal.realmObjectCompanionOrNull
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

/**
 * This interface is used to determine if a Realm file should be compacted the first time the file
 * is opened and before the instance is returned.
 *
 * Note that compacting a file can take a while, so compacting should generally only be done as
 * part of opening a Realm on a background thread.
 */
public fun interface CompactOnLaunchCallback {

    /**
     * This method determines if the Realm file should be compacted before opened and returned to
     * the user.
     *
     * @param totalBytes the total file size (data + free space).
     * @param usedBytes the total bytes used by data in the file.
     * @return `true` to indicate an attempt to compact the file should be made. Otherwise,
     * compaction will be skipped.
     */
    public fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean
}

/**
 * This interface is used to write data to a Realm file when the file is first created.
 * It will be used in a way similar to using [Realm.writeBlocking].
 *
 * Note that writing data to a Realm file will involve IO, so it should generally only be done as
 * part of opening a Realm on a background thread.
 */
public fun interface InitialDataCallback {
    /**
     * Creates a write transaction in which the initial data can be written with
     * [MutableRealm] as a receiver. This mirrors the API when using [Realm.write]
     * and allows for the following pattern:
     *
     * ```
     * val config = RealmConfiguration.Builder()
     *   .initialData { // this: MutableRealm
     *       copyToRealm(Person("Jane Doe"))
     *   }
     *   .build()
     * val realm = Realm.open(config)
     * ```
     */
    public fun MutableRealm.write()
}

/**
 * Configuration for log events created by a Realm instance.
 */
@Deprecated("Use io.realm.kotlin.log.RealmLog instead.")
public data class LogConfiguration(
    /**
     * The [LogLevel] for which all log events of equal or higher priority will be reported.
     */
    public val level: LogLevel,

    /**
     * Any loggers to install. They will receive all log events with a priority equal to or higher
     * than the value defined in [LogConfiguration.level].
     */
    public val loggers: List<RealmLogger>
)

/**
 * Configuration for pre-bundled asset files used as initial state of the realm file.
 */
public data class InitialRealmFileConfiguration(
    /**
     * Path to the realm file. This will be interpreted differently depending on the platform. See [SharedBuilder.initialRealmFile] for details.
     */
    val assetFile: String,
    /**
     * Asset file SHA256-checksum used to verify the integrity of the asset file. See
     * [SharedBuilder.initialRealmFile] for details.
     */
    val checksum: String?
)

@ExperimentalEncryptionCallbackApi
public interface EncryptionKeyCallback {
    /**
     * Provides the native memory address of the 64 byte array containing the key used to encrypt and decrypt the Realm file.
     */
    public fun keyPointer(): Long

    /**
     * This callback will be invoked by Realm after it's open. This hint to the user that the key provided in [keyPointer] can now be released.
     */
    public fun releaseKey()
}

/**
 * Base configuration options shared between all realm configuration types.
 */
public interface Configuration {

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
    public val schema: Set<KClass<out BaseRealmObject>>

    /**
     * The log configuration used for the realm instance.
     */
    @Deprecated("Use io.realm.kotlin.log.RealmLog instead.")
    public val log: LogConfiguration

    /**
     * Maximum number of active versions.
     *
     * Holding references to objects from previous version of the data in the realm will also
     * require keeping the data in the actual file. This can cause growth of the file. See
     * [SharedBuilder.maxNumberOfActiveVersions] for details.
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
    public val encryptionKey: ByteArray?

    /**
     * Native memory address of the 64 byte array containing the key used to encrypt and decrypt the Realm file.
     *
     * @return null on unencrypted Realms.
     */
    @OptIn(ExperimentalEncryptionCallbackApi::class)
    public val encryptionKeyAsCallback: EncryptionKeyCallback?

    /**
     * Callback that determines if the realm file should be compacted as part of opening it.
     *
     * @return `null` if the realm file should not be compacted when opened. Otherwise, the callback
     * returned is the one that will be invoked in order to determine if the file should be
     * compacted or not.
     * @see [RealmConfiguration.Builder.compactOnLaunch]
     */
    public val compactOnLaunchCallback: CompactOnLaunchCallback?

    /**
     * Callback that will be triggered in order to write initial data when the Realm file is
     * created for the first time.
     *
     * The callback has a [MutableRealm]] as a receiver, which allows for the following pattern:
     *
     * ```
     * val config = RealmConfiguration.Builder()
     *   .initialData { // this: MutableRealm
     *       copyToRealm(Person("Jane Doe"))
     *   }
     *   .build()
     * val realm = Realm.open(config)
     * ```
     *
     * @return `null` if no initial data should be written when opening a Realm file, otherwise
     * the callback return is the one responsible for writing the data.
     * @see [RealmConfiguration.Builder.initialDataCallback]
     */
    public val initialDataCallback: InitialDataCallback?

    /**
     * Describes whether the realm should reside in memory or on disk.
     */
    public val inMemory: Boolean

    /**
     * Configuration that holds details of a bundled asset file used as initial state of the realm
     * file. See [SharedBuilder.initialRealmFile] for details. `null` is returned if no initial realm
     * file has been configured.
     */
    public val initialRealmFileConfiguration: InitialRealmFileConfiguration?

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
    public abstract class SharedBuilder<T, S : SharedBuilder<T, S>>(
        protected var schema: Set<KClass<out BaseRealmObject>> = setOf()
    ) {

        init {
            // Verify that the schema only contains subclasses of RealmObject and EmbeddedRealmObject
            schema.forEach { clazz: KClass<out BaseRealmObject> ->
                if (clazz.realmObjectCompanionOrNull() == null) {
                    throw IllegalArgumentException(
                        "Only subclasses of RealmObject and " +
                            "EmbeddedRealmObject are allowed in the schema. Found: ${clazz.qualifiedName}. " +
                            "If ${clazz.qualifiedName} is a valid subclass: $MISSING_PLUGIN_MESSAGE"
                    )
                }
            }
        }

        // 'name' must be nullable as it is optional when getting SyncClient's default path!
        protected abstract var name: String?
        protected var logLevel: LogLevel = RealmLog.level
        protected var appConfigLoggers: List<RealmLogger> = listOf()
        protected var realmConfigLoggers: List<RealmLogger> = listOf()
        protected var maxNumberOfActiveVersions: Long = Long.MAX_VALUE
        protected var notificationDispatcher: CoroutineDispatcher? = null
        protected var writeDispatcher: CoroutineDispatcher? = null
        protected var schemaVersion: Long = 0
        protected var encryptionKey: ByteArray? = null
        @OptIn(ExperimentalEncryptionCallbackApi::class)
        protected var encryptionKeyAsCallback: EncryptionKeyCallback? = null
        protected var compactOnLaunchCallback: CompactOnLaunchCallback? = null
        protected var initialDataCallback: InitialDataCallback? = null
        protected var inMemory: Boolean = false
        protected var initialRealmFileConfiguration: InitialRealmFileConfiguration? = null

        /**
         * Sets the filename of the realm file.
         *
         * @throws IllegalArgumentException if the name includes a path separator or the name is
         * `.realm`.
         */
        public abstract fun name(name: String): S

        /**
         * Creates the RealmConfiguration based on the builder properties.
         *
         * @return the created RealmConfiguration.
         *
         * @throws IllegalStateException if trying to build a configuration with incompatible
         * options.
         */
        public abstract fun build(): T

        /**
         * Sets the maximum number of live versions in the Realm file before an
         * [IllegalStateException] is thrown when attempting to write more data.
         *
         * Realm is capable of concurrently handling many different versions of Realm objects, this
         * can e.g. happen if a flow is slow to process data from the database while a fast writer
         * is putting data into the Realm.
         *
         * Under normal circumstances this is not a problem, but if the number of active versions
         * grow too large, it will have a negative effect on the file size on disk. Setting this
         * parameters can therefore be used to prevent uses of Realm that can result in very large
         * file sizes.
         *
         * For details see the *Large Realm file size*-section of the
         * [FAQ](https://docs.mongodb.com/realm-legacy/docs/java/latest/index.html#faq)
         *
         * @param number the maximum number of active versions before an exception is thrown.
         */
        public fun maxNumberOfActiveVersions(maxVersions: Long = 8): S = apply {
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
         * installed by default that will redirect to the common logging framework on the platform,
         * i.e. LogCat on Android and NSLog on iOS.
         */
        @Deprecated("Use io.realm.kotlin.log.RealmLog instead.")
        public open fun log(
            level: LogLevel = LogLevel.WARN,
            customLoggers: List<RealmLogger> = emptyList()
        ): S = apply {
            this.logLevel = level
            this.realmConfigLoggers = customLoggers
        } as S

        /**
         * Dispatcher on which Realm notifications are run. It is possible to listen for changes to
         * Realm objects from any thread, but the underlying logic will run on this dispatcher
         * before any changes are returned to the receiving context.
         *
         * Defaults to a single threaded dispatcher started when the configuration is built.
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher dispatcher on which notifications are run. It is required to be backed
         * by a single thread only.
         */
        internal fun notificationDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.notificationDispatcher = dispatcher
        } as S

        /**
         * Dispatcher used to run background writes to the Realm.         *
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher dispatcher on which writes are run. It is required to be backed by a
         * single thread only.
         */
        internal fun writeDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.writeDispatcher = dispatcher
        } as S

        /**
         * Sets the schema version of the Realm. This must be equal to or higher than the schema
         * version of the existing Realm file, if any. If the schema version is higher than the
         * already existing Realm, a migration is needed.
         */
        public fun schemaVersion(schemaVersion: Long): S {
            if (schemaVersion < 0) {
                throw IllegalArgumentException("Realm schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion")
            }
            return apply { this.schemaVersion = schemaVersion } as S
        }

        /**
         * Sets the 64 byte key used to encrypt and decrypt the Realm file. If no key is provided
         * the Realm file will be unencrypted.
         *
         * It is important that this key is created and stored securely. See
         * [this link](https://docs.mongodb.com/realm/sdk/android/advanced-guides/encryption/) for
         * suggestions on how to do that.
         *
         * @param encryptionKey 64-byte encryption key.
         */
        public fun encryptionKey(encryptionKey: ByteArray): S =
            apply { this.encryptionKey = validateEncryptionKey(encryptionKey) } as S

        /**
         * Similar to [encryptionKey] but instead this will read the encryption key from native memory.
         * This can enhance the security of the app, since it reduces the window where the key is available in clear
         * in memory (avoid memory dump attack). Once the Realm is open, one can zero-out the memory region holding the key
         * as it will be already passed to the C++ storage engine.
         *
         * There's also extra protection for JVM Windows target, where the underlying storage engine uses the Windows Kernel
         * to encrypt/decrypt the Realm's encryption key before each usage.
         *
         *
         * Note: The RealmConfiguration doesn't take ownership of this native memory, the caller is responsible of disposing it
         * appropriately after the Realm is open using the [EncryptionKeyCallback.releaseKey].
         *
         * @param encryptionKeyAsCallback Callback providing address/pointer to a 64-byte array containing the AES encryption key.
         * This array should be in native memory to avoid copying the key into garbage collected heap memory (for JVM targets).
         *
         * One way to create such an array in JVM is to use JNI or use `sun.misc.Unsafe` as follow:
         *
         *```
         * import sun.misc.Unsafe
         *
         * val field = Unsafe::class.java.getDeclaredField("theUnsafe")
         * field.isAccessible = true
         * val unsafe: Unsafe = field.get(null) as Unsafe
         *
         * val key = Random.nextBytes(64) // Replace with your actual AES key
         * val keyPointer: Long = unsafe.allocateMemory(key.size.toLong())
         *  for (i in key.indices) { // Write the key bytes to native memory
         *      unsafe.putByte(keyPointer + i, key[i])
         *  }
         *
         * val encryptedConf = RealmConfiguration
         *      .Builder(schema = setOf(Sample::class))
         *             .encryptionKey(object : EncryptionKeyCallback {
         *                 override fun keyPointer() = keyPointer
         *                 override fun releaseKey() = unsafe.freeMemory(keyPointer)
         *             })
         *             .build()
         *
         * val realm = Realm.open(encryptedConf)
         *```
         */
        @OptIn(ExperimentalEncryptionCallbackApi::class)
        public fun encryptionKey(encryptionKeyAsCallback: EncryptionKeyCallback): S =
            apply { this.encryptionKeyAsCallback = encryptionKeyAsCallback } as S

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
         * is defined, the default callback will be used. See
         * [Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK] for more details.
         */
        public fun compactOnLaunch(callback: CompactOnLaunchCallback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK): S =
            apply { this.compactOnLaunchCallback = callback } as S

        /**
         * Writes initial data to the Realm file. This callback will be executed only once, when
         * the database file is created. This also include cases where
         * [RealmConfiguration.Builder.deleteRealmIfMigrationNeeded] was set causing the file to be
         * deleted.
         *
         * The callback will happen on the same thread used when using [Realm.writeBlocking].
         *
         * @param callback callback used to write data to the Realm file.
         */
        public fun initialData(callback: InitialDataCallback): S =
            apply { initialDataCallback = callback } as S

        /**
         * Setting this will create an in-memory Realm instead of saving it to disk. In-memory Realms might still use
         * disk space if memory is running low, but all files created by an in-memory Realm will be deleted when the
         * Realm is closed.
         *
         * Note that because in-memory Realms are not persisted, you must be sure to hold on to at least one non-closed
         * reference to the in-memory Realm instance as long as you want the data to last.
         */
        public fun inMemory(): S =
            apply { this.inMemory = true } as S

        /**
         * Initializes a realm file with a bundled asset realm file.
         *
         * When opening the realm for the first time the realm file is initialized from the given
         * [assetFile]. This only happens if the realm files at [path] not already exists.
         *
         * The asset file is sought located on the platform's conventional locations for bundled
         * assets/resources:
         * - Android: Through android.content.res.AssetManager.open(assetFilename)
         * - JVM: Class<T>.javaClass.classLoader.getResource(assetFilename)
         * - Darwin: NSBundle.mainBundle.pathForResource(assetFilenameBase, assetFilenameExtension)
         * And it is the responsibility of the developer to place the files at the appropriate
         * location.
         *
         * This cannot be combined with [inMemory] or
         * [RealmConfiguration.Builder.deleteRealmIfMigrationNeeded]. Attempts to do so will cause
         * [build] to throw an [IllegalStateException].
         *
         * NOTE: This could potentially be a lengthy operation, so opening a Realm with a predefined
         * asset file should ideally be done on a background thread.
         * NOTE: There is currently no protection against multiple processes trying to copy the
         * asset file in place at the same time, so user must ensure that only one process is trying
         * to trigger this at a time.
         *
         * @param assetFile the name of the assetFile in the platform's default asset/resource
         * location. If the asset file cannot be located when opening the realm for the first time
         * [Realm.open] will fail with an [IllegalArgumentException].
         * @param sha256checkSum a SHA256-checksum used to verify the integrity of the asset file.
         * If this is specified and the checksum does not match the computed checksum of the
         * [assetFile] when the realm is opened the first time [Realm.open] will fail with a
         * [IllegalArgumentException].
         *
         * @throws IllegalArgumentException if called with an empty [assetFile].
         */
        public fun initialRealmFile(assetFile: String, sha256checkSum: String? = null): S {
            require(assetFile.isNotEmpty()) {
                "Asset file must be a non-empty filename."
            }
            require(sha256checkSum == null || sha256checkSum.isNotEmpty()) {
                "Checksum must be null or a non-empty string."
            }
            this.initialRealmFileConfiguration = InitialRealmFileConfiguration(assetFile, sha256checkSum)
            return this as S
        }

        protected fun validateEncryptionKey(encryptionKey: ByteArray): ByteArray {
            if (encryptionKey.size != Realm.ENCRYPTION_KEY_LENGTH) {
                throw IllegalArgumentException("The provided key must be ${Realm.ENCRYPTION_KEY_LENGTH} bytes. The provided key was ${encryptionKey.size} bytes.")
            }
            return encryptionKey
        }

        protected fun checkName(name: String) {
            require(name.isNotEmpty()) {
                "A non-empty filename must be provided."
            }
            require(!name.contains(PATH_SEPARATOR)) {
                "Name cannot contain path separator '$PATH_SEPARATOR': '$name'"
            }
            require(name != REALM_FILE_EXTENSION) {
                "'$REALM_FILE_EXTENSION' is not a valid filename"
            }
        }

        protected open fun verifyConfig() {
            initialRealmFileConfiguration?.let {
                if (inMemory) {
                    throw IllegalStateException("Cannot combine `initialRealmFile` and `inMemory` configuration options")
                }
            }
        }
    }
}
