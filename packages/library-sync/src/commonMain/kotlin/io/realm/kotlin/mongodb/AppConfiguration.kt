/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.mongodb

import io.ktor.client.plugins.logging.Logger
import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.internal.CoreExceptionConverter
import io.realm.kotlin.internal.RealmLog
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.canWrite
import io.realm.kotlin.internal.platform.createDefaultSystemLogger
import io.realm.kotlin.internal.platform.directoryExists
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.platform.prepareRealmDirectoryPath
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.mongodb.internal.KtorNetworkTransport
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.CoroutineDispatcher

/**
 * An **AppConfiguration** is used to setup linkage to an Atlas App Services Application.
 *
 * Instances of an AppConfiguration can be created by using the [AppConfiguration.Builder] and
 * calling its [AppConfiguration.Builder.build] method or by using [AppConfiguration.create].
 */
public interface AppConfiguration {

    public val appId: String
    // TODO Consider replacing with URL type, but didn't want to include io.ktor.http.Url as it
    //  requires ktor as api dependency
    public val baseUrl: String
    public val encryptionKey: ByteArray?
    public val networkTransport: NetworkTransport
    public val metadataMode: MetadataMode
    public val syncRootDirectory: String

    public companion object {
        /**
         * The default url for App Services applications.
         *
         * @see Builder#baseUrl(String)
         */
        public const val DEFAULT_BASE_URL: String = "https://realm.mongodb.com"

        /**
         * The default header name used to carry authorization data when making network requests
         * towards App Services.
         */
        public const val DEFAULT_AUTHORIZATION_HEADER_NAME: String = "Authorization"

        /**
         * Creates an app configuration with a given [appId] with default values for all
         * optional configuration parameters.
         *
         * @param appId the application id of the App Services Application.
         */
        public fun create(appId: String): AppConfiguration = AppConfiguration.Builder(appId).build()
    }

    /**
     * Builder used to construct instances of an [AppConfiguration] in a fluent manner.
     *
     * @param appId the application id of the App Services Application.
     */
    public class Builder(
        private val appId: String
    ) {

        init {
            CoreExceptionConverter.initialize()
        }

        private var baseUrl: String = DEFAULT_BASE_URL
        // TODO We should use a multi threaded dispatcher
        //  https://github.com/realm/realm-kotlin/issues/501
        private var dispatcher: CoroutineDispatcher = singleThreadDispatcher("dispatcher-$appId")
        private var encryptionKey: ByteArray? = null
        private var logLevel: LogLevel = LogLevel.WARN
        private var removeSystemLogger: Boolean = false
        private var syncRootDirectory: String = appFilesDirectory()
        private var userLoggers: List<RealmLogger> = listOf()

        /**
         * Sets the encryption key used to encrypt user metadata only. Individual Realms need to
         * use [SyncConfiguration.Builder.encryptionKey] to encrypt them.
         *
         * @param key a 64 byte encryption key.
         * @return the Builder instance used.
         * @throws IllegalArgumentException if the key is not 64 bytes long.
         */
        public fun encryptionKey(key: ByteArray): Builder = apply {
            if (key.size != Realm.ENCRYPTION_KEY_LENGTH) {
                throw IllegalArgumentException("The provided key must be ${Realm.ENCRYPTION_KEY_LENGTH} bytes. Yours was: ${key.size}.")
            }

            this.encryptionKey = key.copyOf()
        }

        /**
         * Sets the base url for the App Services Application. The default value is
         * [DEFAULT_BASE_URL].
         *
         * @param baseUrl the base url for the App Services Application.
         */
        public fun baseUrl(baseUrl: String): Builder = apply { this.baseUrl = baseUrl }

        /**
         * The dispatcher used to execute internal tasks; most notably remote HTTP requests.
         */
        public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply { this.dispatcher = dispatcher }

        /**
         * Configures how Realm will report log events for this App.
         *
         * @param level all events at this level or higher will be reported.
         * @param customLoggers any custom loggers to send log events to. A default system logger is
         * installed by default that will redirect to the common logging framework on the platform, i.e.
         * LogCat on Android and NSLog on iOS.
         */
        public fun log(level: LogLevel = LogLevel.WARN, customLoggers: List<RealmLogger> = emptyList()): Builder =
            apply {
                this.logLevel = level
                this.userLoggers = customLoggers
            }

        /**
         * Configures the root folder that marks the location of a `mongodb-realm` folder. This
         * folder contains all files and realms used when synchronizing data between the device and
         * Atlas using Device Sync.
         *
         * The default root directory is platform-dependent:
         * ```
         * // For Android the default directory is obtained using
         * val dir = "${Context.getFilesDir()}"
         *
         * // For JVM platforms the default directory is obtained using
         * val dir = "${System.getProperty("user.dir")}"
         *
         * // For macOS the default directory is obtained using
         * val dir = "${NSFileManager.defaultManager.currentDirectoryPath}"
         *
         * // For iOS the default directory is obtained using
         * val dir = "${NSFileManager.defaultManager.URLForDirectory(
         *      NSDocumentDirectory,
         *      NSUserDomainMask,
         *      null,
         *      true,
         *      null
         * )}"
         * ```
         *
         * @param rootDir the directory where a `mongodb-realm` directory will be created.
         */
        public fun syncRootDirectory(rootDir: String): Builder = apply {
            val directoryExists = directoryExists(rootDir)
            if (!directoryExists && fileExists(rootDir)) {
                throw IllegalArgumentException("'rootDir' is a file, not a directory: $rootDir.")
            }
            if (!directoryExists) {
                prepareRealmDirectoryPath(rootDir)
            }
            if (!canWrite(rootDir)) {
                throw IllegalArgumentException("Realm directory is not writable: $rootDir.")
            }
            this.syncRootDirectory = rootDir
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
        internal fun removeSystemLogger(): Builder = apply { this.removeSystemLogger = true }

        /**
         * Creates the AppConfiguration from the properties of the builder.
         *
         * @return the AppConfiguration that can be used to create a [App].
         */
        public fun build(): AppConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)
            val appLogger = RealmLog(configuration = LogConfiguration(this.logLevel, allLoggers))

            val networkTransport: NetworkTransport = KtorNetworkTransport(
                // FIXME Add AppConfiguration.Builder option to set timeout as a Duration with default \
                //  constant in AppConfiguration.Companion
                //  https://github.com/realm/realm-kotlin/issues/408
                timeoutMs = 15000,
                dispatcher = dispatcher,
                logger = object : Logger {
                    override fun log(message: String) {
                        appLogger.debug(message)
                    }
                }
            ).freeze() // Kotlin network client needs to be frozen before passed to the C-API

            return AppConfigurationImpl(
                appId = appId,
                baseUrl = baseUrl,
                encryptionKey = encryptionKey,
                networkTransport = networkTransport,
                syncRootDirectory = syncRootDirectory,
                log = appLogger
            )
        }
    }
}
