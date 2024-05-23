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
package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.Configuration
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.ConfigurationImpl
import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.SchemaMode
import io.realm.kotlin.internal.platform.PATH_SEPARATOR
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.internal.SyncConfigurationImpl
import io.realm.kotlin.mongodb.internal.UserImpl
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * This enum determines how Realm sync data with the server.
 *
 * The server must be configured for the selected way, otherwise an error will be
 * reported to [SyncConfiguration.errorHandler] when the Realm connects to the server
 * for the first time.
 */
public enum class SyncMode {
    /**
     * Partition-based Sync. Data is selected for synchronization based on a _partition key_,
     * which is a property that must be set on all objects. Server objects that
     * match a given _partition value_ are then synchronized to the device.
     *
     * **See:** [Partitions](https://www.mongodb.com/docs/atlas/app-services/sync/data-access-patterns/partitions/)
     */
    PARTITION_BASED,

    /**
     * Flexible Sync. Data is selected for synchronization based on one or more queries which are
     * stored in a [SubscriptionSet]. All server objects that match one or more queries are then
     * synchronized to the device.
     *
     * **See:** [Flexible Sync](https://www.mongodb.com/docs/atlas/app-services/sync/data-access-patterns/flexible-sync/)
     */
    FLEXIBLE
}

/**
 * Callback used to populate the initial [SubscriptionSet] when opening a Realm.
 *
 * This is configured through [SyncConfiguration.Builder.initialSubscriptions].
 */
public fun interface InitialSubscriptionsCallback {
    /**
     * Closure for adding or modifying the initial [SubscriptionSet], with the
     * [MutableSubscriptionSet] as the receiver. This mirrors the API when using
     * [SubscriptionSet.update] and allows for the following pattern:
     *
     * ```
     * val user = loginUser()
     * val config = SyncConfiguration.Builder(user, schema)
     *   .initialSubscriptions { realm: Realm -> // this: MutableSubscriptionSet
     *       add(realm.query<Person>())
     *   }
     *   .waitForInitialRemoteData(timeout = 30.seconds)
     *   .build()
     * val realm = Realm.open(config)
     * ```
     */
    public fun MutableSubscriptionSet.write(realm: Realm)
}

/**
 * Configuration options if [SyncConfiguration.Builder.waitForInitialRemoteData] is
 * enabled.
 */
public data class InitialRemoteDataConfiguration(

    /**
     * The timeout used when downloading any initial data server the first time the
     * Realm is opened.
     *
     * If the timeout is hit, opening a Realm will throw an
     * [io.realm.mongodb.exceptions.DownloadingRealmTimeOutException].
     */
    val timeout: Duration = Duration.INFINITE
)

/**
 * Configuration options if [SyncConfiguration.Builder.initialSubscriptions] is
 * enabled.
 */
public data class InitialSubscriptionsConfiguration(

    /**
     * The callback that will be called in order to populate the initial
     * [SubscriptionSet] for the realm.
     */
    val callback: InitialSubscriptionsCallback,

    /**
     * The default behavior is that [callback] is only invoked the first time
     * the Realm is opened, but if [rerunOnOpen] is `true`, it will be invoked
     * every time the realm is opened.
     */
    val rerunOnOpen: Boolean
)

/**
 * A [SyncConfiguration] is used to setup a Realm Database that can be synchronized between
 * devices using Atlas Device Sync.
 *
 * A valid [User] is required to create a [SyncConfiguration]. See
 * [Credentials] and [App.login] for more information on how to get a user object.
 *
 * A minimal [SyncConfiguration] can be found below.
 * ```
 *      val app = App.create(appId)
 *      val user = app.login(Credentials.anonymous())
 *      val config = SyncConfiguration.create(user, "partition-value", setOf(YourRealmObject::class))
 *      val realm = Realm.open(config)
 * ```
 */
public interface SyncConfiguration : Configuration {

    public val user: User

    // FIXME Hide this for now, as we should should not expose an internal class like this.
    //  Currently this is only available from `SyncConfigurationImpl`.
    //  See https://github.com/realm/realm-kotlin/issues/815
    // public val partitionValue: PartitionValue
    public val errorHandler: SyncSession.ErrorHandler

    /**
     * Strategy used to handle client reset scenarios.
     *
     * The SDK reads and writes to a local realm file. When Device Sync is enabled, the local realm
     * syncs with the application backend. Some conditions can cause the realm to be unable to sync
     * with Atlas. When this occurs, a client reset error is issued by the server.
     *
     * There is one constraint users need to be aware of when defining customized strategies when
     * creating a configuration. Flexible Sync applications can **only** work in conjunction
     * with [ManuallyRecoverUnsyncedChangesStrategy] whereas partition-based applications can
     * **only** work in conjunction with [DiscardUnsyncedChangesStrategy].
     *
     * If not specified, default strategies defined in [Builder.build] will be used, depending on
     * whether the application has Flexible Sync enabled. Setting this parameter manually will
     * override the use of either default strategy.
     *
     * **See:** [Client Reset](https://www.mongodb.com/docs/realm/sdk/java/examples/reset-a-client-realm/)
     */
    public val syncClientResetStrategy: SyncClientResetStrategy

    /**
     * The mode of synchronization for this realm.
     */
    public val syncMode: SyncMode

    /**
     * Configuration options if initial subscriptions have been enabled for this
     * realm.
     *
     * If this has not been enabled, `null` is returned.
     *
     * @see SyncConfiguration.Builder.initialSubscriptions
     */
    public val initialSubscriptions: InitialSubscriptionsConfiguration?

    /**
     * Configuration options if downloading initial data from the server has been
     * enabled for this realm.
     *
     * If this has not been enabled, `null` is returned.
     *
     * @see SyncConfiguration.Builder.waitForInitialRemoteData
     */
    public val initialRemoteData: InitialRemoteDataConfiguration?

    /**
     * Used to create a [SyncConfiguration]. For common use cases, a [SyncConfiguration] can be
     * created using the [SyncConfiguration.create] function.
     */
    public class Builder private constructor(
        private var user: User,
        schema: Set<KClass<out BaseRealmObject>>,
        private var partitionValue: BsonValue?,
    ) : Configuration.SharedBuilder<SyncConfiguration, Builder>(schema) {

        // Shouldn't default to 'default.realm' - Object Store will generate it according to which
        // type of Sync is used
        protected override var name: String? = null

        private var errorHandler: SyncSession.ErrorHandler? = null
        private var syncClientResetStrategy: SyncClientResetStrategy? = null
        private var initialSubscriptions: InitialSubscriptionsConfiguration? = null
        private var waitForServerChanges: InitialRemoteDataConfiguration? = null

        /**
         * Creates a [SyncConfiguration.Builder] for Flexible Sync. Flexible Sync must be enabled
         * on the server for this to work.
         *
         * **See:** [Flexible Sync](https://www.mongodb.com/docs/realm/sync/data-access-patterns/flexible-sync/)
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(user, schema, null)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with an [BsonObjectId](https://www.mongodb.com/docs/v4.4/reference/method/ObjectId/) partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * **See:** [partition key](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         */
        public constructor(
            user: User,
            partitionValue: BsonObjectId?,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(user, schema, partitionValue ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a [RealmUUID] partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * **See:** [partition key](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         */
        public constructor(
            user: User,
            partitionValue: RealmUUID?,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(
            user,
            schema,
            partitionValue?.let {
                BsonBinary(
                    type = BsonBinarySubType.UUID_STANDARD,
                    data = it.bytes
                )
            } ?: BsonNull
        )

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a Int partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: Int?,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(user, schema, partitionValue?.let { BsonInt32(it) } ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a Long partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: Long?,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(user, schema, partitionValue?.let { BsonInt64(it) } ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a String partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: String?,
            schema: Set<KClass<out BaseRealmObject>>
        ) : this(user, schema, partitionValue?.let { BsonString(it) } ?: BsonNull)

        init {
            if (!user.loggedIn) {
                throw IllegalArgumentException("A valid, logged in user is required.")
            }
        }

        /**
         * Sets the error handler used by Synced Realms when reporting errors with their session.
         *
         * @param errorHandler lambda to handle the error.
         */
        public fun errorHandler(errorHandler: SyncSession.ErrorHandler): Builder =
            apply { this.errorHandler = errorHandler }

        /**
         * Sets the strategy that would handle the client reset by this synced Realm.
         *
         * In case no strategy is defined the default one,
         * [RecoverOrDiscardUnsyncedChangesStrategy], will be used.
         *
         * @param resetStrategy custom strategy to handle client reset.
         */
        public fun syncClientResetStrategy(resetStrategy: SyncClientResetStrategy): Builder =
            apply {
                this.syncClientResetStrategy = resetStrategy
            }

        /**
         * Sets the filename of the realm file.
         *
         * If a [SyncConfiguration] is built without having provided a [name], Realm will
         * generate a file name based on the provided [partitionValue] and [AppConfiguration.appId]
         * which will have a `.realm` extension.
         *
         * @throws IllegalArgumentException if the name includes a path separator or if the name is
         * `.realm`.
         */
        override fun name(name: String): Builder = apply {
            checkName(name)
            this.name = name
        }

        /**
         * Setting this will cause the Realm to download all known changes from the server the
         * first time a Realm is opened. The Realm will not open until all the data has been
         * downloaded. This means that if a device is offline the Realm will not open.
         *
         * Since downloading all changes can be a lengthy operation that might block the UI
         * thread, Realms with this setting enabled should only be opened on background threads.
         *
         * This check is only enforced the first time a Realm is created, except if
         * [initialSubscriptions] has been configured with `rerunOnOpen = true`. In that case,
         * server data is downloaded every time the Realm is opened.
         *
         * If it is conditional when server data should be downloaded, this can be controlled
         * through [SyncSession.downloadAllServerChanges], e.g like this:
         *
         * ```
         * val user = loginUser()
         * val config = SyncConfiguration.Builder(user, schema)
         *     .initialSubscriptions { realm ->
         *         add(realm.query<City>())
         *     }
         *     .build()
         * val realm = Realm.open(config)
         * if (downloadData) {
         *     realm.syncSession.downloadAllServerChanges(timeout = 30.seconds)
         * }
         * ```
         *
         * @param timeout how long to wait for the download to complete before an
         * [io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException] is thrown when opening
         * the Realm.
         */
        public fun waitForInitialRemoteData(timeout: Duration = Duration.INFINITE): Builder =
            apply {
                this.waitForServerChanges = InitialRemoteDataConfiguration(timeout)
            }

        /**
         * Define the initial [io.realm.mongodb.sync.SubscriptionSet] for the Realm. This will only
         * be executed the first time the Realm file is opened (and the file created).
         *
         * If [waitForInitialRemoteData] is configured as well, the realm file isn't fully
         * opened until all subscription data also has been downloaded.
         *
         * @param rerunOnOpen If `true` this closure will rerun every time the Realm is opened,
         * this makes it possible to update subscription queries with e.g. new timestamp information
         * or other query data that might change over time. If [waitForInitialRemoteData] is also
         * set, the Realm will download the new subscription data every time the Realm is opened,
         * rather than just the first time.
         * @param initialSubscriptionBlock closure making it possible to modify the set of
         * subscriptions.
         */
        public fun initialSubscriptions(
            rerunOnOpen: Boolean = false,
            initialSubscriptionBlock: InitialSubscriptionsCallback
        ): Builder = apply {
            if (partitionValue != null) {
                throw IllegalStateException(
                    "Defining initial subscriptions is only available if " +
                        "the configuration is for Flexible Sync."
                )
            }
            this.initialSubscriptions = InitialSubscriptionsConfiguration(
                initialSubscriptionBlock,
                rerunOnOpen
            )
        }

        @Suppress("LongMethod")
        override fun build(): SyncConfiguration {
            val realmLogger = ContextLogger()

            // Set default error handler after setting config logging logic
            if (this.errorHandler == null) {
                this.errorHandler = object : SyncSession.ErrorHandler {
                    override fun onError(session: SyncSession, error: SyncException) {
                        error.message?.let {
                            realmLogger.warn(it)
                        }
                    }
                }
            }

            // Don't forget: Flexible Sync only supports Manual Client Reset
            if (syncClientResetStrategy == null) {
                syncClientResetStrategy = object : RecoverOrDiscardUnsyncedChangesStrategy {
                    override fun onBeforeReset(realm: TypedRealm) {
                        realmLogger.info("Client reset: attempting to automatically recover unsynced changes in Realm: ${realm.configuration.path}")
                    }

                    override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                        realmLogger.info("Client reset: successfully recovered all unsynced changes in Realm: ${after.configuration.path}")
                    }

                    override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                        realmLogger.info("Client reset: couldn't recover successfully, all unsynced changes were discarded in Realm: ${after.configuration.path}")
                    }

                    override fun onManualResetFallback(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        realmLogger.error("Client reset: manual reset required for Realm in '${exception.originalFilePath}'")
                    }
                }
            }

            // ObjectStore uses a different default value for Flexible Sync than we want,
            // so inject our default name if no user provided name was found
            if (partitionValue == null && name == null) {
                name = Realm.DEFAULT_FILE_NAME
            }
            val fullPathToFile = getAbsolutePath(name)
            val fileName = fullPathToFile.substringAfterLast(PATH_SEPARATOR)
            val directory = fullPathToFile.removeSuffix("$PATH_SEPARATOR$fileName")

            val baseConfiguration = ConfigurationImpl(
                directory,
                fileName,
                schema,
                maxNumberOfActiveVersions,
                if (notificationDispatcher != null) {
                    CoroutineDispatcherFactory.unmanaged(notificationDispatcher!!)
                } else {
                    CoroutineDispatcherFactory.managed("notifier-$fileName")
                },
                if (writeDispatcher != null) {
                    CoroutineDispatcherFactory.unmanaged(writeDispatcher!!)
                } else {
                    CoroutineDispatcherFactory.managed("writer-$fileName")
                },
                schemaVersion,
                SchemaMode.RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED,
                encryptionKey,
                compactOnLaunchCallback,
                null, // migration is not relevant for sync,
                false, // automatic backlink handling is not relevant for sync
                initialDataCallback,
                partitionValue == null,
                inMemory,
                initialRealmFileConfiguration,
                realmLogger
            )

            return SyncConfigurationImpl(
                baseConfiguration,
                partitionValue,
                user as UserImpl,
                errorHandler!!, // It will never be null: either default or user-provided
                syncClientResetStrategy!!, // It will never be null: either default or user-provided
                initialSubscriptions,
                waitForServerChanges
            )
        }

        private fun getAbsolutePath(name: String?): String {
            // In order for us to generate the path we need to provide a full-fledged sync
            // configuration which at this point we don't yet have, so we have to create a
            // temporary one so that we can return the actual path to a sync Realm using the
            // realm_app_sync_client_get_default_file_path_for_realm function below
            val absolutePath: String = when (partitionValue == null) {
                true -> RealmInterop.realm_flx_sync_config_new((user as UserImpl).nativePointer)
                false -> RealmInterop.realm_sync_config_new(
                    (user as UserImpl).nativePointer,
                    partitionValue!!.toJson()
                )
            }.let { auxSyncConfig ->
                RealmInterop.realm_app_sync_client_get_default_file_path_for_realm(
                    auxSyncConfig,
                    name
                )
            }

            return absolutePath
        }
    }

    public companion object {

        /**
         * Creates a sync configuration for Flexible Sync with default values for all
         * optional configuration parameters.
         *
         * Flexible Sync uses a concept called subscription sets to define which data gets
         * uploaded and downloaded to the device. See [SubscriptionSet] for more information.
         *
         * **See:** [Flexible Sync](https://www.mongodb.com/docs/atlas/app-services/sync/data-access-patterns/flexible-sync/)
         *
         * @param user the [User] who controls the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(user: User, schema: Set<KClass<out BaseRealmObject>>): SyncConfiguration =
            Builder(user, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user the [User] who controls the realm.
         * @param partitionValue the partition value that defines which data to sync to the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: String?,
            schema: Set<KClass<out BaseRealmObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user the [User] who controls the realm.
         * @param partitionValue the partition value that defines which data to sync to the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: Int?,
            schema: Set<KClass<out BaseRealmObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * * **See:** [Partitions](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         *
         * @param user the [User] who controls the realm.
         * @param partitionValue the partition value that defines which data to sync to the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: Long?,
            schema: Set<KClass<out BaseRealmObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * @param user the [User] who controls the realm.
         * @param partitionValue the partition value that defines which data to sync to the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         * * **See:** [partition key](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         */
        public fun create(user: User, partitionValue: BsonObjectId?, schema: Set<KClass<out BaseRealmObject>>): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * @param user the [User] who controls the realm.
         * @param partitionValue the partition value that defines which data to sync to the realm.
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         * * **See:** [partition key](https://www.mongodb.com/docs/realm/sync/data-access-patterns/partitions/)
         */
        public fun create(user: User, partitionValue: RealmUUID?, schema: Set<KClass<out BaseRealmObject>>): SyncConfiguration =
            Builder(user, partitionValue, schema).build()
    }
}
