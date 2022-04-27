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

package io.realm.mongodb.sync

import io.realm.Configuration
import io.realm.LogConfiguration
import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.ConfigurationImpl
import io.realm.internal.REALM_FILE_EXTENSION
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.SchemaMode
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.platform.PATH_SEPARATOR
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.exceptions.SyncException
import io.realm.mongodb.internal.SyncConfigurationImpl
import io.realm.mongodb.internal.UserImpl
import kotlin.reflect.KClass

/**
 * A [SyncConfiguration] is used to setup a Realm Database that can be synchronized between
 * devices using MongoDB Realm.
 *
 * A valid [User] is required to create a [SyncConfiguration]. See
 * [Credentials] and [App.login] for more information on how to get a user object.
 *
 * A minimal [SyncConfiguration] can be found below.
 * ```
 *      val app = App.create(appId)
 *      val user = app.login(Credentials.anonymous())
 *      val config = SyncConfiguration.Builder(user, "partition-value", setOf(YourRealmObject::class)).build()
 *      val realm = Realm.open(config)
 * ```
 */
// FIXME update docs when `with` is ready: https://github.com/realm/realm-kotlin/issues/504
public interface SyncConfiguration : Configuration {

    public val user: User
    public val partitionValue: PartitionValue
    public val errorHandler: SyncSession.ErrorHandler?

    /**
     * Used to create a [SyncConfiguration]. For common use cases, a [SyncConfiguration] can be
     * created using the [RealmConfiguration.with] function.
     */
    public class Builder private constructor(
        private var user: User,
        private var partitionValue: PartitionValue,
        schema: Set<KClass<out RealmObject>>,
    ) : Configuration.SharedBuilder<SyncConfiguration, Builder>(schema) {

        // Shouldn't default to 'default.realm' - Object Store will generate it according to which
        // type of Sync is used
        protected override var name: String? = null

        private var errorHandler: SyncSession.ErrorHandler? = null

        public constructor(
            user: User,
            partitionValue: Int,
            schema: Set<KClass<out RealmObject>>
        ) : this(user, PartitionValue(partitionValue.toLong()), schema)

        public constructor(
            user: User,
            partitionValue: Long,
            schema: Set<KClass<out RealmObject>>
        ) : this(user, PartitionValue(partitionValue), schema)

        public constructor(
            user: User,
            partitionValue: String,
            schema: Set<KClass<out RealmObject>>
        ) : this(user, PartitionValue(partitionValue), schema)

        init {
            // Prime builder with log configuration from AppConfiguration
            val appLogConfiguration = (user as UserImpl).app.configuration.log.configuration
            this.logLevel = appLogConfiguration.level
            this.userLoggers = appLogConfiguration.loggers
            this.removeSystemLogger = true
        }

        /**
         * Sets the error handler used by Synced Realms when reporting errors with their session.
         *
         * @param errorHandler lambda to handle the error.
         */
        public fun errorHandler(errorHandler: SyncSession.ErrorHandler): Builder =
            apply { this.errorHandler = errorHandler }

        override fun log(level: LogLevel, customLoggers: List<RealmLogger>): Builder =
            apply {
                // Will clear any primed configuration
                this.logLevel = level
                this.userLoggers = customLoggers
                this.removeSystemLogger = false
            }

        /**
         * Sets the filename of the realm file.
         *
         * MongoDB Realm will internally append `.realm` to the specified filename, except when the
         * name itself already contains `.realm` as its extension. For example:
         * - `my_db.database` will become `my_db.database.realm`
         * - `my_db.realm` will remain `my_db.realm`
         * - `my_db` will become `my_db.realm`
         *
         * @throws IllegalArgumentException if the name includes a path separator or if the name is
         * `.realm`.
         */
        override fun name(name: String): Builder = apply {
            checkName(name)

            // Strip `.realm` suffix as it will be appended by Object Store later
            this.name = if (name.endsWith(REALM_FILE_EXTENSION)) {
                name.substringBeforeLast(REALM_FILE_EXTENSION)
            } else {
                name
            }
        }

        override fun build(): SyncConfiguration {
            val allLoggers = userLoggers.toMutableList()
            // TODO This will not remove the system logger if it was added in AppConfiguration and
            //  no overrides are done for this builder. But as removeSystemLogger() is not public
            //  and most people will only specify loggers on the AppConfiguration this is OK for
            //  now.
            if (!removeSystemLogger) {
                allLoggers.add(0, createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }

            // Set default error handler after setting config logging logic
            if (this.errorHandler == null) {
                this.errorHandler = object : SyncSession.ErrorHandler {

                    private val fallbackErrorLogger: RealmLogger by lazy {
                        createDefaultSystemLogger("SYNC_ERROR")
                    }

                    override fun onError(session: SyncSession, error: SyncException) {
                        error.message?.let {
                            // Grab user logger if present or use fallback to at least show something
                            // in case no loggers are to be found
                            if (userLoggers.isNotEmpty()) {
                                userLoggers[0].log(LogLevel.WARN, it)
                            } else {
                                fallbackErrorLogger.log(LogLevel.WARN, it)
                            }
                        }
                    }
                }
            }

            // Don't use 'Realm.DEFAULT_FILE_NAME' if 'name()' hasn't been used to set it
            val fullPathToFile = getAbsolutePath(name)
            val fileName = fullPathToFile.substringAfterLast(PATH_SEPARATOR)
            val directory = fullPathToFile.removeSuffix("$PATH_SEPARATOR$fileName")

            val baseConfiguration = ConfigurationImpl(
                directory,
                fileName,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(fileName),
                writeDispatcher ?: singleThreadDispatcher(fileName),
                schemaVersion,
                SchemaMode.RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED,
                encryptionKey,
                compactOnLaunchCallback,
                null // migration is not relevant for sync
            )

            return SyncConfigurationImpl(
                baseConfiguration,
                partitionValue,
                user as UserImpl,
                errorHandler!! // It will never be null: either default or user-provided
            )
        }

        private fun getAbsolutePath(name: String?): String {
            // In order for us to generate the path we need to provide a full-fledged sync
            // configuration which at this point we don't yet have, so we have to create a
            // temporary one so that we can return the actual path to a sync Realm using the
            // realm_app_sync_client_get_default_file_path_for_realm function below
            val absolutePath: String = RealmInterop.realm_sync_config_new(
                (user as UserImpl).nativePointer,
                partitionValue.asSyncPartition()
            ).let { auxSyncConfig ->
                RealmInterop.realm_app_sync_client_get_default_file_path_for_realm(
                    (user as UserImpl).app.nativePointer,
                    auxSyncConfig,
                    name
                )
            }
            return absolutePath
        }
    }
}
