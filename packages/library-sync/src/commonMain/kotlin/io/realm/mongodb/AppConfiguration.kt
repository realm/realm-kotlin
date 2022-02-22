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

package io.realm.mongodb

import io.ktor.client.features.logging.Logger
import io.realm.LogConfiguration
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.internal.RealmLog
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.freeze
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import io.realm.mongodb.internal.AppConfigurationImpl
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * An **AppConfiguration** is used to setup linkage to a MongoDB Realm application.
 *
 * Instances of an AppConfiguration can only be created by using the [AppConfiguration.Builder] and
 * calling its [AppConfiguration.Builder.build] method.
 */
public interface AppConfiguration {

    public val appId: String
    // TODO Consider replacing with URL type, but didn't want to include io.ktor.http.Url as it
    //  requires ktor as api dependency
    public val baseUrl: String
    public val networkTransport: NetworkTransport
    public val metadataMode: MetadataMode

    public companion object {
        /**
         * The default url for MongoDB Realm applications.
         *
         * @see Builder#baseUrl(String)
         */
        public const val DEFAULT_BASE_URL: String = "https://realm.mongodb.com"

        /**
         * The default header name used to carry authorization data when making network requests
         * towards MongoDB Realm.
         */
        public const val DEFAULT_AUTHORIZATION_HEADER_NAME: String = "Authorization"
    }

    /**
     * Builder used to construct instances of an [AppConfiguration] in a fluent manner.
     *
     * @param appId the application id of the MongoDB Realm Application.
     */
    public class Builder(
        private val appId: String
    ) {
        private var baseUrl: String = DEFAULT_BASE_URL
        // TODO We should use a multi threaded dispatcher
        //  https://github.com/realm/realm-kotlin/issues/501
        private var dispatcher: CoroutineDispatcher = singleThreadDispatcher("dispatcher-$appId")

        private var logLevel: LogLevel = LogLevel.WARN
        private var removeSystemLogger: Boolean = false
        private var userLoggers: List<RealmLogger> = listOf()

        /**
         * Sets the base url for the MongoDB Realm Application. The default value is
         * [DEFAULT_BASE_URL].
         *
         * @param baseUrl the base url for the MongoDB Realm application.
         */
        public fun baseUrl(baseUrl: String): Builder = apply { this.baseUrl = baseUrl }

        /**
         * The dispatcher used to execute internal tasks; most notably remote HTTP requests.
         */
        public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply { this.dispatcher = dispatcher }

        /**
         * Configure how Realm will report log events for this App.
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
                timeoutMs = 5000,
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
                networkTransport = networkTransport,
                log = appLogger
            )
        }
    }
}
