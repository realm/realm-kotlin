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
import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.canWrite
import io.realm.kotlin.internal.platform.directoryExists
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.prepareRealmDirectoryPath
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.internal.util.DispatcherHolder
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.ext.customData
import io.realm.kotlin.mongodb.ext.profile
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.mongodb.internal.KtorNetworkTransport
import io.realm.kotlin.mongodb.internal.LogObfuscatorImpl
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

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
    public val metadataMode: MetadataMode
    public val syncRootDirectory: String

    /**
     * Custom configured headers that will be sent alongside other headers when
     * making network requests towards Atlas App services.
     */
    public val customRequestHeaders: Map<String, String>

    /**
     * Authorization header name used for Atlas App services requests.
     */
    public val authorizationHeaderName: String

    /**
     * The name of app. This is only used for debugging.
     *
     * @see [AppConfiguration.Builder.appName]
     */
    public val appName: String?

    /**
     * The version of the app. This is only used for debugging.
     *
     * @see [AppConfiguration.Builder.appVersion]
     */
    public val appVersion: String?

    /**
     * The default EJson decoder that would be used to encode and decode arguments and results
     * when calling remote App [Functions], authenticating with a [customFunction], and retrieving
     * a user [profile] or [customData].
     *
     * It can be set with [Builder.ejson] if a certain configuration, such as contextual classes, is
     * required.
     */
    @OptIn(ExperimentalKBsonSerializerApi::class)
    public val ejson: EJson

    /**
     * The configured [HttpLogObfuscator] for this app. If this property returns `null` no
     * obfuscator is being used.
     */
    public val httpLogObfuscator: HttpLogObfuscator?

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
        private val appId: String,
    ) {

        private var baseUrl: String = DEFAULT_BASE_URL
        private var dispatcher: CoroutineDispatcher? = null
        private var encryptionKey: ByteArray? = null
        private var logLevel: LogLevel? = null
        private var syncRootDirectory: String = appFilesDirectory()
        private var userLoggers: List<RealmLogger> = listOf()
        private var networkTransport: NetworkTransport? = null
        private var appName: String? = null
        private var appVersion: String? = null

        @OptIn(ExperimentalKBsonSerializerApi::class)
        private var ejson: EJson = EJson
        private var httpLogObfuscator: HttpLogObfuscator? = LogObfuscatorImpl
        private val customRequestHeaders = mutableMapOf<String, String>()
        private var authorizationHeaderName: String = DEFAULT_AUTHORIZATION_HEADER_NAME

        /**
         * Sets the encryption key used to encrypt the user metadata Realm only. Individual
         * Realms need to use [SyncConfiguration.Builder.encryptionKey] to encrypt them.
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
         * @return the Builder instance used.
         */
        public fun baseUrl(baseUrl: String): Builder = apply { this.baseUrl = baseUrl }

        /**
         * The dispatcher used to execute internal tasks; most notably remote HTTP requests.
         *
         * @return the Builder instance used.
         */
        public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply {
            this.dispatcher = dispatcher
        }

        /**
         * Configures how Realm will report log events for this App.
         *
         * @param level all events at this level or higher will be reported.
         * @param customLoggers any custom loggers to send log events to. A default system logger is
         * installed by default that will redirect to the common logging framework on the platform, i.e.
         * LogCat on Android and NSLog on iOS.
         * @return the Builder instance used.
         */
        @Deprecated("Use io.realm.kotlin.log.RealmLog instead.")
        public fun log(
            level: LogLevel = LogLevel.WARN,
            customLoggers: List<RealmLogger> = emptyList(),
        ): Builder =
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
         * @return the Builder instance used.
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
         * Sets the debug app name which is added to debug headers for App Services network
         * requests. The default is `null`.
         *
         * @param appName app name used to identify the application.
         * @throws IllegalArgumentException if an empty [appName] is provided.
         * @return the Builder instance used.
         */
        public fun appName(appName: String): Builder = apply {
            Validation.checkEmpty(appName, "appName")
            this.appName = appName
        }

        /**
         * Sets the debug app version which is added to debug headers for App Services network
         * requests. The default is `null`
         *
         * @param appVersion app version used to identify the application.
         * @throws IllegalArgumentException if an empty [appVersion] is provided.
         * @return the Builder instance used.
         */
        public fun appVersion(appVersion: String): Builder = apply {
            Validation.checkEmpty(appVersion, "appVersion")
            this.appVersion = appVersion
        }

        /**
         * Sets the a [HttpLogObfuscator] used to keep sensitive information in HTTP requests from
         * being displayed in the log. Logs containing tokens, passwords or custom function
         * arguments and the result of computing these will be obfuscated by default. Logs will not
         * be obfuscated if the value is set to `null`.
         *
         * @param httpLogObfuscator the HTTP log obfuscator to be used or `null` if obfuscation
         * should be disabled.
         * @return the Builder instance used.
         */
        public fun httpLogObfuscator(httpLogObfuscator: HttpLogObfuscator?): Builder = apply {
            this.httpLogObfuscator = httpLogObfuscator
        }

        /**
         * Sets the name of the HTTP header used to send authorization data in when making requests to
         * Atlas App Services. The Atlas App or firewall must have been configured to expect a
         * custom authorization header.
         *
         * The default authorization header is named [DEFAULT_AUTHORIZATION_HEADER_NAME].
         *
         * @param name name of the header.
         */
        public fun authorizationHeaderName(name: String): Builder = apply {
            authorizationHeaderName = name
        }

        /**
         * Adds an extra HTTP header to append to every request to an Atlas App Services Application.
         *
         * @param name the name of the header.
         * @param value the value of header.
         */
        public fun addCustomRequestHeader(name: String, value: String): Builder = apply {
            customRequestHeaders[name] = value
        }

        /**
         * Adds extra HTTP headers to append to every request to an Atlas App Services Application.
         *
         * @param headers map with the headers to add.
         */
        public fun addCustomRequestHeaders(headers: Map<String, String>): Builder = apply {
            customRequestHeaders.putAll(headers)
        }

        /**
         * Sets the default EJson decoder that would be use to encode and decode arguments and results
         * when calling remote Atlas [Functions], authenticating with a [customFunction], and retrieving
         * a user [profile] or [customData].
         */
        @ExperimentalRealmSerializerApi
        @OptIn(ExperimentalKBsonSerializerApi::class)
        public fun ejson(ejson: EJson): Builder = apply {
            this.ejson = ejson
        }

        /**
         * Allows defining a custom network transport. It is used by some tests that require simulating
         * network responses.
         */
        internal fun networkTransport(networkTransport: NetworkTransport?): Builder = apply {
            this.networkTransport = networkTransport
        }

        /**
         * Creates the AppConfiguration from the properties of the builder.
         *
         * @return the AppConfiguration that can be used to create a [App].
         */
        public fun build(): AppConfiguration {
            // We cannot rewire this to build(bundleId) and just have REPLACED_BY_IR here,
            // as these calls might be in a module where the compiler plugin hasn't been applied.
            // In that case we don't setup the correct bundle ID. If this is an issue we could maybe
            // just force users to apply our plugin.
            return build("UNKNOWN_BUNDLE_ID")
        }

        // This method is used to inject bundleId to the sync configuration. The
        // SyncLoweringExtension is replacing calls to SyncConfiguration.Builder.build() with calls
        // to this method.
        @OptIn(ExperimentalKBsonSerializerApi::class)
        public fun build(bundleId: String): AppConfiguration {
            // Configure logging during creation of AppConfiguration to keep old behavior for
            // configuring logging. This should be removed when `LogConfiguration` is removed.
            val allLoggers = mutableListOf<RealmLogger>()
            allLoggers.addAll(userLoggers)

            val logConfig = this.logLevel?.let {
                RealmLog.level = it
                LogConfiguration(it, allLoggers)
            }

            userLoggers.forEach { RealmLog.add(it) }

            val appNetworkDispatcherFactory = if (dispatcher != null) {
                CoroutineDispatcherFactory.unmanaged(dispatcher!!)
            } else {
                // TODO We should consider using a multi threaded dispatcher. Ktor already does
                //  this under the hood though, so it is unclear exactly what benefit there is.
                //  https://github.com/realm/realm-kotlin/issues/501
                CoroutineDispatcherFactory.managed("app-dispatcher-$appId")
            }

            val appLogger = ContextLogger("Sdk")
            val networkTransport: (dispatcher: DispatcherHolder) -> NetworkTransport =
                { dispatcherHolder ->
                    val logger: Logger = object : Logger {
                        override fun log(message: String) {
                            val obfuscatedMessage = httpLogObfuscator?.obfuscate(message)
                            appLogger.debug(obfuscatedMessage ?: message)
                        }
                    }
                    networkTransport ?: KtorNetworkTransport(
                        // FIXME Add AppConfiguration.Builder option to set timeout as a Duration with default \
                        //  constant in AppConfiguration.Companion
                        //  https://github.com/realm/realm-kotlin/issues/408
                        timeoutMs = 60000,
                        dispatcherHolder = dispatcherHolder,
                        logger = logger,
                        customHeaders = customRequestHeaders,
                        authorizationHeaderName = authorizationHeaderName
                    )
                }

            return AppConfigurationImpl(
                appId = appId,
                baseUrl = baseUrl,
                encryptionKey = encryptionKey,
                metadataMode = if (encryptionKey == null)
                    MetadataMode.RLM_SYNC_CLIENT_METADATA_MODE_PLAINTEXT
                else MetadataMode.RLM_SYNC_CLIENT_METADATA_MODE_ENCRYPTED,
                appNetworkDispatcherFactory = appNetworkDispatcherFactory,
                networkTransportFactory = networkTransport,
                syncRootDirectory = syncRootDirectory,
                logger = logConfig,
                appName = appName,
                appVersion = appVersion,
                bundleId = bundleId,
                ejson = ejson,
                httpLogObfuscator = httpLogObfuscator,
                customRequestHeaders = customRequestHeaders,
                authorizationHeaderName = authorizationHeaderName,
            )
        }
    }
}
