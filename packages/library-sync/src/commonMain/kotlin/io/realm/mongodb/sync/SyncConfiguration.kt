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
package io.realm.mongodb.sync

import android.content.Context
import org.bson.BsonInt32
import java.io.File
import java.io.UnsupportedEncodingException
import java.lang.*
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Boolean
import kotlin.Long
import kotlin.collections.Iterable
import kotlin.jvm.JvmOverloads

/**
 * A [SyncConfiguration] is used to setup a Realm Database that can be synchronized between
 * devices using MongoDB Realm.
 *
 *
 * A valid [User] is required to create a [SyncConfiguration]. See
 * [Credentials] and [App.loginAsync] for
 * more information on how to get a user object.
 *
 *
 * A minimal [SyncConfiguration] can be found below.
 * <pre>
 * `App app = new App("app-id");
 * User user = app.login(Credentials.anonymous());
 * SyncConfiguration config = SyncConfiguration.defaultConfiguration(user, "partition-value");
 * Realm realm = Realm.getInstance(config);
` *
</pre> *
 *
 *
 * Synchronized Realms only support additive migrations which can be detected and performed
 * automatically, so the following builder options are not accessible compared to a normal Realm:
 *
 *
 *  * `deleteRealmIfMigrationNeeded()`
 *  * `migration(Migration)`
 *
 *
 * Synchronized Realms are created by using [Realm.getInstance] and
 * [Realm.getDefaultInstance] like ordinary unsynchronized Realms.
 *
 * @see [The docs](https://docs.realm.io/platform/using-synced-realms/syncing-data) for
 * more information about the two types of synchronization.
 */
@Beta
class SyncConfiguration private constructor(
    realmPath: File,
    @Nullable assetFilePath: kotlin.String?,
    @Nullable key: ByteArray,
    schemaVersion: Long,
    @Nullable migration: RealmMigration?,
    deleteRealmIfMigrationNeeded: Boolean,
    durability: OsRealmConfig.Durability,
    schemaMediator: RealmProxyMediator,
    @Nullable rxFactory: RxObservableFactory?,
    @Nullable flowFactory: FlowFactory?,
    @Nullable initialDataTransaction: Realm.Transaction?,
    readOnly: Boolean,
    maxNumberOfActiveVersions: Long,
    allowWritesOnUiThread: Boolean,
    allowQueriesOnUiThread: Boolean,
    user: User?,
    serverUrl: URI,
    errorHandler: io.realm.mongodb.sync.SyncSession.ErrorHandler?,
    clientResetHandler: io.realm.mongodb.sync.SyncSession.ClientResetHandler?,
    deleteRealmOnLogout: Boolean,
    waitForInitialData: Boolean,
    initialDataTimeoutMillis: Long,
    sessionStopPolicy: OsRealmConfig.SyncSessionStopPolicy,
    compactOnLaunch: CompactOnLaunchCallback?,
    @Nullable syncUrlPrefix: kotlin.String?,
    clientResyncMode: ClientResyncMode,
    partitionValue: BsonValue
) : RealmConfiguration(
    realmPath,
    assetFilePath,
    key,
    schemaVersion,
    migration,
    deleteRealmIfMigrationNeeded,
    durability,
    schemaMediator,
    rxFactory,
    flowFactory,
    initialDataTransaction,
    readOnly,
    compactOnLaunch,
    false,
    maxNumberOfActiveVersions,
    allowWritesOnUiThread,
    allowQueriesOnUiThread
) {
    /**
     * Returns the server URI for the remote MongoDB Realm the local Realm is synchronizing with.
     *
     * @return [URI] identifying the MongoDB Realm this local Realm is synchronized with.
     */
    val serverUrl: URI
    private val user: User?
    private val errorHandler: io.realm.mongodb.sync.SyncSession.ErrorHandler?
    private val clientResetHandler: io.realm.mongodb.sync.SyncSession.ClientResetHandler?
    private val deleteRealmOnLogout: Boolean
    private val waitForInitialData: Boolean
    private val initialDataTimeoutMillis: Long
    private val sessionStopPolicy: OsRealmConfig.SyncSessionStopPolicy

    /**
     * Returns the url prefix used when establishing a sync connection to the Realm Object Server.
     */
    @get:Nullable
    @Nullable
    val urlPrefix: kotlin.String?

    /**
     * Returns what happens in case of a Client Resync.
     */
    val clientResyncMode: ClientResyncMode
    private val partitionValue: BsonValue
    fun forErrorRecovery(canonicalPath: kotlin.String?): RealmConfiguration {
        return RealmConfiguration.forRecovery(
            canonicalPath,
            getEncryptionKey(),
            getSchemaMediator()
        )
    }

    protected val initialDataTransaction: Realm.Transaction
        protected get() = super.getInitialDataTransaction()

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    override fun equals(@Nullable o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        if (!super.equals(o)) return false
        val that = o as SyncConfiguration
        if (deleteRealmOnLogout != that.deleteRealmOnLogout) return false
        if (waitForInitialData != that.waitForInitialData) return false
        if (initialDataTimeoutMillis != that.initialDataTimeoutMillis) return false
        if (serverUrl != that.serverUrl) return false
        if (!user.equals(that.user)) return false
        if (errorHandler != that.errorHandler) return false
        if (sessionStopPolicy !== that.sessionStopPolicy) return false
        if (if (urlPrefix != null) urlPrefix != that.urlPrefix else that.urlPrefix != null) return false
        return if (clientResyncMode !== that.clientResyncMode) false else partitionValue.equals(that.partitionValue)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + serverUrl.hashCode()
        result = 31 * result + user.hashCode()
        result = 31 * result + errorHandler.hashCode()
        result = 31 * result + if (deleteRealmOnLogout) 1 else 0
        result = 31 * result + if (waitForInitialData) 1 else 0
        result =
            31 * result + (initialDataTimeoutMillis xor (initialDataTimeoutMillis ushr 32)).toInt()
        result = 31 * result + sessionStopPolicy.hashCode()
        result = 31 * result + if (urlPrefix != null) urlPrefix.hashCode() else 0
        result = 31 * result + clientResyncMode.hashCode()
        result = 31 * result + partitionValue.hashCode()
        return result
    }

    override fun toString(): kotlin.String {
        val sb = StringBuilder(super.toString())
        sb.append("\n")
        sb.append("serverUrl: ").append(serverUrl)
        sb.append("\n")
        sb.append("user: ").append(user)
        sb.append("\n")
        sb.append("errorHandler: ").append(errorHandler)
        sb.append("\n")
        sb.append("deleteRealmOnLogout: ").append(deleteRealmOnLogout)
        sb.append("\n")
        sb.append("waitForInitialData: ").append(waitForInitialData)
        sb.append("\n")
        sb.append("initialDataTimeoutMillis: ").append(initialDataTimeoutMillis)
        sb.append("\n")
        sb.append("sessionStopPolicy: ").append(sessionStopPolicy)
        sb.append("\n")
        sb.append("syncUrlPrefix: ").append(urlPrefix)
        sb.append("\n")
        sb.append("clientResyncMode: ").append(clientResyncMode)
        sb.append("\n")
        sb.append("partitionValue: ").append(partitionValue)
        return sb.toString()
    }

    /**
     * Returns the user.
     *
     * @return the user.
     */
    fun getUser(): User? {
        return user
    }

    /**
     * Returns the error handler for this *SyncConfiguration*.
     *
     * @return the error handler.
     */
    fun getErrorHandler(): io.realm.mongodb.sync.SyncSession.ErrorHandler? {
        return errorHandler
    }

    /**
     * Returns the Client Reset handler for this *SyncConfiguration*.
     *
     * @return the Client Reset handler.
     */
    fun getClientResetHandler(): io.realm.mongodb.sync.SyncSession.ClientResetHandler? {
        return clientResetHandler
    }

    /**
     * Returns `true` if the Realm file must be deleted once the [User] owning it logs out.
     *
     * @return `true` if the Realm file must be deleted if the [User] logs out. `false` if the file
     * is allowed to remain behind.
     */
    fun shouldDeleteRealmOnLogout(): Boolean {
        return deleteRealmOnLogout
    }

    /**
     * Returns `true` if the Realm will download all known changes from the remote server before being opened the
     * first time.
     *
     * @return `true` if all remote changes will be downloaded before the Realm can be opened. `false` if
     * the Realm can be opened immediately.
     */
    fun shouldWaitForInitialRemoteData(): Boolean {
        return waitForInitialData
    }

    /**
     * Returns the timeout defined when downloading any initial data the first time the Realm is opened.
     *
     *
     * This value is only applicable if [.shouldWaitForInitialRemoteData] returns `true`.
     *
     * @return the time Realm will wait for all changes to be downloaded before it is aborted and an exception is thrown.
     * @see Builder.waitForInitialRemoteData
     */
    fun getInitialRemoteDataTimeout(unit: TimeUnit): Long {
        return unit.convert(initialDataTimeoutMillis, TimeUnit.MILLISECONDS)
    }

    protected val isSyncConfiguration: Boolean
        protected get() = true

    /**
     * NOTE: Only for internal usage. May change without warning.
     *
     * Returns the stop policy for the session for this Realm once the Realm has been closed.
     *
     * @return the stop policy used by the session once the Realm is closed.
     */
    fun getSessionStopPolicy(): OsRealmConfig.SyncSessionStopPolicy {
        return sessionStopPolicy
    }

    /**
     * Returns the value this Realm is partitioned on. The partition key is a property defined in
     * MongoDB Realm. All classes with a property with this value will be synchronized to the
     * Realm.
     *
     * @return the value being used by MongoDB Realm to partition the server side MongoDB Database
     * into Realms that can be synchronized independently.
     */
    fun getPartitionValue(): BsonValue {
        return partitionValue
    }

    protected fun realmExists(): Boolean {
        return super.realmExists()
    }

    /**
     * Builder used to construct instances of a SyncConfiguration in a fluent manner.
     */
    class Builder internal constructor(user: User, partitionValue: BsonValue) {
        @Nullable
        private var key: ByteArray
        private var schemaVersion: Long = 0
        private val modules = HashSet<Any>()
        private val debugSchema: HashSet<Class<out RealmModel?>> = HashSet<Class<out RealmModel?>>()

        @Nullable
        private var rxFactory: RxObservableFactory? = null

        @Nullable
        private var flowFactory: FlowFactory? = null

        @Nullable
        private var initialDataTransaction: Realm.Transaction? = null

        @Nullable
        private var filename: kotlin.String? = null
        private var durability: OsRealmConfig.Durability = OsRealmConfig.Durability.FULL
        private var readOnly = false
        private var waitForServerChanges = false
        private var initialDataTimeoutMillis = Long.MAX_VALUE

        // sync specific
        private val deleteRealmOnLogout = false
        private var serverUrl: URI? = null
        private var user: User? = null
        private var errorHandler: io.realm.mongodb.sync.SyncSession.ErrorHandler? = null
        private var clientResetHandler: io.realm.mongodb.sync.SyncSession.ClientResetHandler? = null
        private var sessionStopPolicy: OsRealmConfig.SyncSessionStopPolicy =
            OsRealmConfig.SyncSessionStopPolicy.AFTER_CHANGES_UPLOADED
        private var compactOnLaunch: CompactOnLaunchCallback? = null
        private var syncUrlPrefix: kotlin.String? = null

        @Nullable // null means the user hasn't explicitly set one. An appropriate default is chosen when calling build()
        private var clientResyncMode: ClientResyncMode? = null
        private var maxNumberOfActiveVersions = Long.MAX_VALUE
        private var allowWritesOnUiThread = false
        private var allowQueriesOnUiThread = false
        private val partitionValue: BsonValue

        /**
         * Creates an instance of the builder for a *SyncConfiguration* with the given user
         * and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         */
        constructor(user: User?, @Nullable partitionValue: kotlin.String?) : this(
            user,
            partitionValue?.let { BsonString(it) } ?: BsonNull()) {
        }

        /**
         * Creates an instance of the builder for a *SyncConfiguration* with the given user
         * and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         */
        constructor(user: User?, @Nullable partitionValue: ObjectId?) : this(
            user,
            if (partitionValue == null) BsonNull() else BsonObjectId(partitionValue)
        ) {
        }

        /**
         * Creates an instance of the builder for a *SyncConfiguration* with the given user
         * and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         */
        constructor(user: User?, @Nullable partitionValue: Int?) : this(
            user,
            partitionValue?.let { BsonInt32(it) } ?: BsonNull()) {
        }

        /**
         * Creates an instance of the builder for a *SyncConfiguration* with the given user
         * and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         */
        constructor(user: User?, @Nullable partitionValue: Long?) : this(
            user,
            partitionValue?.let { BsonInt64(it) } ?: BsonNull()) {
        }

        private fun validateAndSet(user: User?) {
            requireNotNull(user) { "Non-null `user` required." }
            require(user.isLoggedIn()) { "User not authenticated or authentication expired." }
            this.user = user
        }

        private fun validateAndSet(baseUrl: URL) {
            serverUrl = try {
                URI(baseUrl.toString())
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("Invalid URI: $baseUrl", e)
            }
            try {
                // Automatically set scheme based on auth server if not set or wrongly set
                var serverScheme = serverUrl!!.getScheme()
                if (serverScheme == null || serverScheme.equals("http", ignoreCase = true)) {
                    serverScheme = "ws"
                } else if (serverScheme.equals("https", ignoreCase = true)) {
                    serverScheme = "wss"
                }

                // Automatically set host if one wasn't defined
                val host = serverUrl!!.getHost()

                // Convert relative paths to absolute if required
                var path = serverUrl!!.getPath()
                if (path != null && !path.startsWith("/")) {
                    path = "/$path"
                }
                serverUrl = URI(
                    serverScheme,
                    serverUrl!!.getUserInfo(),
                    host,
                    serverUrl!!.getPort(),
                    path?.replace(
                        "$host/",
                        ""
                    ),  // Remove host if it accidentially was interpreted as a path segment
                    serverUrl!!.getQuery(),
                    serverUrl!!.getRawFragment()
                )
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("Invalid URI: $baseUrl", e)
            }
        }

        /**
         * FIXME: Make public once https://github.com/realm/realm-object-store/pull/1049 is merged.
         *
         * Sets the filename for the Realm file on this device.
         */
        fun name(filename: kotlin.String?): Builder {
            var filename = filename
            require(!(filename == null || filename.isEmpty())) { "A non-empty filename must be provided" }

            // Strip `.realm` suffix as it will be appended by Object Store later
            if (filename.endsWith(".realm")) {
                filename = if (filename.length == 6) {
                    throw IllegalArgumentException("'.realm' is not a valid filename")
                } else {
                    filename.substring(0, filename.length - 6)
                }
            }
            this.filename = filename
            return this
        }

        /**
         * Sets the {@value io.realm.Realm#ENCRYPTION_KEY_LENGTH} bytes key used to encrypt and decrypt the Realm file.
         *
         * @param key the encryption key.
         * @throws IllegalArgumentException if key is invalid.
         */
        fun encryptionKey(key: ByteArray?): Builder {
            requireNotNull(key) { "A non-null key must be provided" }
            require(key.size == Realm.ENCRYPTION_KEY_LENGTH) {
                String.format(
                    Locale.US,
                    "The provided key must be %s bytes. Yours was: %s",
                    Realm.ENCRYPTION_KEY_LENGTH, key.size
                )
            }
            this.key = Arrays.copyOf(key, key.size)
            return this
        }

        /**
         * DEBUG method. This restricts the Realm schema to only consist of the provided classes without having to
         * create a module. These classes must be available in the default module. Calling this will remove any
         * previously configured modules.
         */
        fun schema(
            firstClass: Class<out RealmModel?>?,
            vararg additionalClasses: Class<out RealmModel?>?
        ): Builder {
            requireNotNull(firstClass) { "A non-null class must be provided" }
            modules.clear()
            modules.add(DEFAULT_MODULE_MEDIATOR)
            debugSchema.add(firstClass)
            if (additionalClasses != null) {
                Collections.addAll<Class<out RealmModel?>>(debugSchema, *additionalClasses)
            }
            return this
        }

        /**
         * DEBUG method. This makes it possible to define different policies for when a session should be stopped when
         * the Realm is closed.
         *
         * @param policy how a session for a Realm should behave when the Realm is closed.
         */
        fun sessionStopPolicy(policy: OsRealmConfig.SyncSessionStopPolicy): Builder {
            sessionStopPolicy = policy
            return this
        }

        /**
         * Sets the schema version of the Realm.
         *
         *
         * Synced Realms only support additive schema changes which can be applied without requiring a manual
         * migration. The schema version will only be used as an indication to the underlying storage layer to remove
         * or add indexes. These will be recalculated if the provided schema version differ from the version in the
         * Realm file.
         *
         * **WARNING:** There is no guarantee that the value inserted here is the same returned by [Realm.getVersion].
         * Due to the nature of synced Realms, the value can both be higher and lower.
         *
         *  * It will be lower if another client with a lesser `schemaVersion` connected to the server for
         * the first time after this schemaVersion was used.
         *
         *  * It will be higher if another client with a higher `schemaVersion` connected to the server after
         * this Realm was created.
         *
         *
         *
         * @param schemaVersion the schema version.
         * @throws IllegalArgumentException if schema version is invalid.
         */
        fun schemaVersion(schemaVersion: Long): Builder {
            require(schemaVersion >= 0) { "Realm schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion" }
            this.schemaVersion = schemaVersion
            return this
        }

        /**
         * Replaces the existing module(s) with one or more [RealmModule]s. Using this method will replace the
         * current schema for this Realm with the schema defined by the provided modules.
         *
         *
         * A reference to the default Realm module containing all Realm classes in the project (but not dependencies),
         * can be found using [Realm.getDefaultModule]. Combining the schema from the app project and a library
         * dependency is thus done using the following code:
         *
         *
         * `builder.modules(Realm.getDefaultMode(), new MyLibraryModule()); `
         *
         *
         * @param baseModule the first Realm module (required).
         * @param additionalModules the additional Realm modules
         * @throws IllegalArgumentException if any of the modules don't have the [RealmModule] annotation.
         * @see Realm.getDefaultModule
         */
        fun modules(baseModule: Any?, vararg additionalModules: Any?): Builder {
            modules.clear()
            addModule(baseModule)
            if (additionalModules != null) {
                for (module in additionalModules) {
                    addModule(module)
                }
            }
            return this
        }

        /**
         * Replaces the existing module(s) with one or more [RealmModule]s. Using this method will replace the
         * current schema for this Realm with the schema defined by the provided modules.
         *
         *
         * A reference to the default Realm module containing all Realm classes in the project (but not dependencies),
         * can be found using [Realm.getDefaultModule]. Combining the schema from the app project and a library
         * dependency is thus done using the following code:
         *
         *
         * `builder.modules(Realm.getDefaultMode(), new MyLibraryModule()); `
         *
         *
         * @param modules list of modules tthe first Realm module (required).
         * @throws IllegalArgumentException if any of the modules don't have the [RealmModule] annotation.
         * @see Realm.getDefaultModule
         */
        fun modules(modules: Iterable<Any?>?): Builder {
            this.modules.clear()
            if (modules != null) {
                for (module in modules) {
                    addModule(module)
                }
            }
            return this
        }

        /**
         * Adds a module to the already defined modules.
         */
        fun addModule(module: Any?): Builder {
            if (module != null) {
                checkModule(module)
                modules.add(module)
            }
            return this
        }

        /**
         * Sets the [RxObservableFactory] used to create Rx Observables from Realm objects.
         * The default factory is [RealmObservableFactory].
         *
         * @param factory factory to use.
         */
        fun rxFactory(@Nonnull factory: RxObservableFactory?): Builder {
            requireNotNull(factory) { "The provided Rx Observable factory must not be null." }
            rxFactory = factory
            return this
        }

        /**
         * Sets the [FlowFactory] used to create coroutines Flows from Realm objects.
         * The default factory is [RealmFlowFactory].
         *
         * @param factory factory to use.
         */
        fun flowFactory(@Nonnull factory: FlowFactory?): Builder {
            requireNotNull(factory) { "The provided Flow factory must not be null." }
            flowFactory = factory
            return this
        }

        /**
         * Sets the initial data in [io.realm.Realm]. This transaction will be executed only the first time
         * the Realm file is opened (created) or while migrating the data if
         * [RealmConfiguration.Builder.deleteRealmIfMigrationNeeded] is set.
         *
         * @param transaction transaction to execute.
         */
        fun initialData(transaction: Realm.Transaction?): Builder {
            initialDataTransaction = transaction
            return this
        }

        /**
         * Setting this will create an in-memory Realm instead of saving it to disk. In-memory Realms might still use
         * disk space if memory is running low, but all files created by an in-memory Realm will be deleted when the
         * Realm is closed.
         *
         *
         * Note that because in-memory Realms are not persisted, you must be sure to hold on to at least one non-closed
         * reference to the in-memory Realm object with the specific name as long as you want the data to last.
         */
        fun inMemory(): Builder {
            durability = OsRealmConfig.Durability.MEM_ONLY
            return this
        }

        /**
         * Sets the error handler used by this configuration.
         *
         *
         * Only errors not handled by the defined `SyncPolicy` will be reported to this error handler.
         *
         * @param errorHandler error handler used to report back errors when communicating with the Realm Object Server.
         * @throws IllegalArgumentException if `null` is given as an error handler.
         */
        fun errorHandler(errorHandler: io.realm.mongodb.sync.SyncSession.ErrorHandler?): Builder {
            Util.checkNull(errorHandler, "handler")
            this.errorHandler = errorHandler
            return this
        }

        /**
         * Sets the handler for when a Client Reset occurs. If no handler is set, and error is
         * logged when a Client Reset occurs.
         *
         * @param handler custom handler in case of a Client Reset.
         */
        fun clientResetHandler(handler: io.realm.mongodb.sync.SyncSession.ClientResetHandler?): Builder {
            Util.checkNull(handler, "handler")
            clientResetHandler = handler
            return this
        }

        /**
         * Setting this will cause the Realm to download all known changes from the server the first time a Realm is
         * opened. The Realm will not open until all the data has been downloaded. This means that if a device is
         * offline the Realm will not open.
         *
         *
         * Since downloading all changes can be an lengthy operation that might block the UI thread, Realms with this
         * setting enabled should only be opened on background threads or with
         * [Realm.getInstanceAsync] on the UI thread.
         *
         *
         * This check is only enforced the first time a Realm is created. If you otherwise want to make sure a Realm
         * has the latest changes, use [SyncSession.downloadAllServerChanges].
         */
        fun waitForInitialRemoteData(): Builder {
            waitForServerChanges = true
            initialDataTimeoutMillis = Long.MAX_VALUE
            return this
        }

        /**
         * Setting this will cause the Realm to download all known changes from the server the first time a Realm is
         * opened. The Realm will not open until all the data has been downloaded. This means that if a device is
         * offline the Realm will not open.
         *
         *
         * Since downloading all changes can be an lengthy operation that might block the UI thread, Realms with this
         * setting enabled should only be opened on background threads or with
         * [Realm.getInstanceAsync] on the UI thread.
         *
         *
         * This check is only enforced the first time a Realm is created. If you otherwise want to make sure a Realm
         * has the latest changes, use [SyncSession.downloadAllServerChanges].
         *
         * @param timeout how long to wait for the download to complete before an [io.realm.exceptions.DownloadingRealmInterruptedException] is thrown.
         * @param unit the unit of time used to define the timeout.
         */
        fun waitForInitialRemoteData(timeout: Long, unit: TimeUnit?): Builder {
            require(timeout >= 0) { "'timeout' must be >= 0. It was: $timeout" }
            requireNotNull(unit) { "Non-null 'unit' required" }
            waitForServerChanges = true
            initialDataTimeoutMillis = unit.toMillis(timeout)
            return this
        }

        /**
         * Setting this will cause the Realm to become read only and all write transactions made against this Realm will
         * fail with an [IllegalStateException].
         *
         *
         * This in particular mean that [.initialData] will not work in combination with a
         * read only Realm and setting this will result in a [IllegalStateException] being thrown.
         *
         * Marking a Realm as read only only applies to the Realm in this process. Other processes and devices can still
         * write to the Realm.
         */
        fun readOnly(): Builder {
            readOnly = true
            return this
        }
        /**
         * Sets this to determine if the Realm file should be compacted before returned to the user. It is passed the
         * total file size (data + free space) and the bytes used by data in the file.
         *
         * @param compactOnLaunch a callback called when opening a Realm for the first time during the life of a process
         * to determine if it should be compacted before being returned to the user. It is passed
         * the total file size (data + free space) and the bytes used by data in the file.
         */
        /**
         * Setting this will cause Realm to compact the Realm file if the Realm file has grown too large and a
         * significant amount of space can be recovered. See [DefaultCompactOnLaunchCallback] for details.
         */
        @JvmOverloads
        fun compactOnLaunch(compactOnLaunch: CompactOnLaunchCallback? = DefaultCompactOnLaunchCallback()): Builder {
            requireNotNull(compactOnLaunch) { "A non-null compactOnLaunch must be provided" }
            this.compactOnLaunch = compactOnLaunch
            return this
        }

        /**
         * The prefix that is prepended to the path in the WebSocket request that initiates a sync
         * connection to MongoDB Realm. The value specified must match the server’s configuration
         * otherwise the device will not be able to create a connection. This value is optional
         * and should only be set if a specific firewall rule requires it.
         *
         * @param urlPrefix The prefix to append to the sync connection url.
         * @see [Adding a custom proxy](https://docs.realm.io/platform/guides/learn-realm-sync-and-integrate-with-a-proxy.adding-a-custom-proxy)
         */
        fun urlPrefix(urlPrefix: kotlin.String): Builder {
            var urlPrefix = urlPrefix
            require(!Util.isEmptyString(urlPrefix)) { "Non-empty 'urlPrefix' required" }
            if (urlPrefix.endsWith("/")) {
                urlPrefix = urlPrefix.substring(0, Math.min(0, urlPrefix.length - 2))
            }
            syncUrlPrefix = urlPrefix
            return this
        }

        private fun MD5(`in`: kotlin.String): kotlin.String {
            return try {
                val digest = MessageDigest.getInstance("MD5")
                val buf = digest.digest(`in`.toByteArray(charset("UTF-8")))
                val builder = StringBuilder()
                for (b in buf) {
                    builder.append(kotlin.String.format(Locale.US, "%02X", b))
                }
                builder.toString()
            } catch (e: NoSuchAlgorithmException) {
                throw RealmException(e.message)
            } catch (e: UnsupportedEncodingException) {
                throw RealmException(e.message)
            }
        }
        /**
         * Setting this will cause the local Realm file used to synchronize changes to be deleted if the [SyncUser]
         * owning this Realm logs out from the device using [SyncUser.logOut].
         *
         *
         * The default behavior is that the Realm file is allowed to stay behind, making it possible for users to log
         * in again and have access to their data faster.
         */
        /* FIXME: Disable this API since we cannot support it without https://github.com/realm/realm-core/issues/2165
        public Builder deleteRealmOnLogout() {
            this.deleteRealmOnLogout = true;
            return this;
        }
        */
        /**
         * TODO: Removed from the public API until MongoDB Realm correctly supports anything byt MANUAL mode again.
         *
         * Configure the behavior in case of a Client Resync.
         *
         *
         * The default mode is [ClientResyncMode.RECOVER_LOCAL_REALM].
         *
         * @param mode what should happen when a Client Resync happens
         * @see ClientResyncMode for more information about what a Client Resync is.
         */
        fun clientResyncMode(mode: ClientResyncMode?): Builder {
            requireNotNull(mode) { "Non-null 'mode' required." }
            clientResyncMode = mode
            return this
        }

        /**
         * Sets the maximum number of live versions in the Realm file before an [IllegalStateException] is thrown when
         * attempting to write more data.
         *
         *
         * Realm is capable of concurrently handling many different versions of Realm objects. This can happen if you
         * have a Realm open on many different threads or are freezing objects while data is being written to the file.
         *
         *
         * Under normal circumstances this is not a problem, but if the number of active versions grow too large, it will
         * have a negative effect on the filesize on disk. Setting this parameters can therefore be used to prevent uses of
         * Realm that can result in very large Realms.
         *
         *
         * Note, the version number will also increase when changes from other devices are integrated on this device,
         * so the number of active versions will also depend on what other devices writing to the same Realm are doing.
         *
         * @param number the maximum number of active versions before an exception is thrown.
         * @see [FAQ](https://realm.io/docs/java/latest/.faq-large-realm-file-size)
         */
        fun maxNumberOfActiveVersions(number: Long): Builder {
            maxNumberOfActiveVersions = number
            return this
        }

        /**
         * Sets whether or not calls to [Realm.executeTransaction] are allowed from the UI thread.
         *
         *
         * **WARNING: Realm does not allow synchronous transactions to be run on the main thread unless users explicitly opt in
         * with this method.** We recommend diverting calls to `executeTransaction` to non-UI threads or, alternatively,
         * using [Realm.executeTransactionAsync].
         */
        fun allowWritesOnUiThread(allowWritesOnUiThread: Boolean): Builder {
            this.allowWritesOnUiThread = allowWritesOnUiThread
            return this
        }

        /**
         * Sets whether or not `RealmQueries` are allowed from the UI thread.
         *
         *
         * By default Realm allows queries on the main thread. However, by doing so your application may experience a drop of
         * frames or even ANRs. We recommend diverting queries to non-UI threads or, alternatively, using
         * [RealmQuery.findAllAsync] or [RealmQuery.findFirstAsync].
         */
        fun allowQueriesOnUiThread(allowQueriesOnUiThread: Boolean): Builder {
            this.allowQueriesOnUiThread = allowQueriesOnUiThread
            return this
        }

        /**
         * Creates the RealmConfiguration based on the builder parameters.
         *
         * @return the created [SyncConfiguration].
         * @throws IllegalStateException if the configuration parameters are invalid or inconsistent.
         */
        fun build(): SyncConfiguration {
            check(!(serverUrl == null || user == null)) { "serverUrl() and user() are both required." }

            // Check that readOnly() was applied to legal configuration. Right now it should only be allowd if
            // an assetFile is configured
            if (readOnly) {
                check(initialDataTransaction == null) {
                    "This Realm is marked as read-only. " +
                            "Read-only Realms cannot use initialData(Realm.Transaction)."
                }
                check(waitForServerChanges) {
                    "A read-only Realms must be provided by some source. " +
                            "'waitForInitialRemoteData()' wasn't enabled which is currently the only supported source."
                }
            }

            // Set the default Client Resync Mode based on the current type of Realm.
            // Eventually RECOVER_LOCAL_REALM should be the default for all types.
            // FIXME: We should add support back for this.
            if (clientResyncMode == null) {
                clientResyncMode = ClientResyncMode.MANUAL
            }
            if (rxFactory == null && Util.isRxJavaAvailable()) {
                rxFactory = RealmObservableFactory(true)
            }
            if (flowFactory == null && Util.isCoroutinesAvailable()) {
                flowFactory = RealmFlowFactory(true)
            }
            val resolvedServerUrl: URI = serverUrl
            syncUrlPrefix = String.format(
                "/api/client/v2.0/app/%s/realm-sync",
                user.getApp().getConfiguration().getAppId()
            )
            val absolutePathForRealm: kotlin.String = user.getApp().getSync()
                .getAbsolutePathForRealm(user.getId(), partitionValue, filename)
            val realmFile = File(absolutePathForRealm)
            return SyncConfiguration(
                realmFile,
                null,  // assetFile not supported by Sync. See https://github.com/realm/realm-sync/issues/241
                key,
                schemaVersion,
                null,  // Custom migrations not supported
                false,  // MigrationNeededException is never thrown
                durability,
                createSchemaMediator(modules, debugSchema, false),
                rxFactory,
                flowFactory,
                initialDataTransaction,
                readOnly,
                maxNumberOfActiveVersions,
                allowWritesOnUiThread,
                allowQueriesOnUiThread,  // Sync Configuration specific
                user,
                resolvedServerUrl,
                errorHandler,
                clientResetHandler,
                deleteRealmOnLogout,
                waitForServerChanges,
                initialDataTimeoutMillis,
                sessionStopPolicy,
                compactOnLaunch,
                syncUrlPrefix,
                clientResyncMode!!,
                partitionValue
            )
        }

        private fun checkModule(module: Any) {
            if (!module.javaClass.isAnnotationPresent(RealmModule::class.java)) {
                throw IllegalArgumentException(
                    module.javaClass.getCanonicalName() + " is not a RealmModule. " +
                            "Add @RealmModule to the class definition."
                )
            }
        }

        /**
         * Builder used to construct instances of a SyncConfiguration in a fluent manner.
         *
         * @param user the user opening the Realm on the server.
         * @param partitionValue te value this Realm is partitioned on. The partition key is a
         * property defined in MongoDB Realm. All classes with a property with this value will be
         * synchronized to the Realm.
         * @see [Link to docs about partions](FIXME)
         */
        init {
            val context: Context = Realm.getApplicationContext()
                ?: throw IllegalStateException("Call `Realm.init(Context)` before creating a SyncConfiguration")
            Util.checkNull(user, "user")
            Util.checkNull(partitionValue, "partitionValue")
            validateAndSet(user)
            validateAndSet(user.getApp().getConfiguration().getBaseUrl())
            this.partitionValue = partitionValue
            if (Realm.getDefaultModule() != null) {
                modules.add(Realm.getDefaultModule())
            }
            errorHandler = user.getApp().getConfiguration().getDefaultErrorHandler()
            clientResetHandler = user.getApp().getConfiguration().getDefaultClientResetHandler()
            allowQueriesOnUiThread = true
            allowWritesOnUiThread = false
        }
    }

    companion object {
        /**
         * Returns a [RealmConfiguration] appropriate to open a read-only, non-synced Realm to recover any pending changes.
         * This is useful when trying to open a backup/recovery Realm (after a client reset).
         *
         * @param canonicalPath the absolute path to the Realm file defined by this configuration.
         * @param encryptionKey the key used to encrypt/decrypt the Realm file.
         * @param modules if specified it will restricts Realm schema to the provided module.
         * @return RealmConfiguration that can be used offline
         */
        fun forRecovery(
            canonicalPath: kotlin.String?,
            @Nullable encryptionKey: ByteArray?,
            @Nullable vararg modules: Any
        ): RealmConfiguration {
            val validatedModules = HashSet<Any>()
            if (modules != null && modules.size > 0) {
                for (module in modules) {
                    if (!module.javaClass.isAnnotationPresent(RealmModule::class.java)) {
                        throw IllegalArgumentException(
                            module.javaClass.getCanonicalName() + " is not a RealmModule. " +
                                    "Add @RealmModule to the class definition."
                        )
                    }
                    validatedModules.add(module)
                }
            } else {
                if (Realm.getDefaultModule() != null) {
                    validatedModules.add(Realm.getDefaultModule())
                }
            }
            val schemaMediator: RealmProxyMediator =
                createSchemaMediator(validatedModules, emptySet<Class<out RealmModel?>>(), false)
            return RealmConfiguration.forRecovery(canonicalPath, encryptionKey, schemaMediator)
        }

        /**
         * Returns a default configuration for the given user and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         * @return the default configuration for the given user and partition value.
         */
        @Beta
        fun defaultConfig(
            user: User?,
            @Nullable partitionValue: kotlin.String?
        ): SyncConfiguration {
            return Builder(user, partitionValue).build()
        }

        /**
         * Returns a default configuration for the given user and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         * @return the default configuration for the given user and partition value.
         */
        @Beta
        fun defaultConfig(user: User?, @Nullable partitionValue: Long?): SyncConfiguration {
            return Builder(user, partitionValue).build()
        }

        /**
         * Returns a default configuration for the given user and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         * @return the default configuration for the given user and partition value.
         */
        @Beta
        fun defaultConfig(user: User?, @Nullable partitionValue: Int?): SyncConfiguration {
            return Builder(user, partitionValue).build()
        }

        /**
         * Returns a default configuration for the given user and partition value.
         *
         * @param user The user that will be used for accessing the Realm App.
         * @param partitionValue The partition value identifying the remote Realm that will be synchronized.
         * @return the default configuration for the given user and partition value.
         */
        @Beta
        fun defaultConfig(user: User?, @Nullable partitionValue: ObjectId?): SyncConfiguration {
            return Builder(user, partitionValue).build()
        }

        /**
         * Returns a [RealmConfiguration] appropriate to open a read-only, non-synced Realm to recover any pending changes.
         * This is useful when trying to open a backup/recovery Realm (after a client reset).
         *
         * Note: This will use the default Realm module (composed of all [RealmModel]), and
         * assume no encryption should be used as well.
         *
         * @param canonicalPath the absolute path to the Realm file defined by this configuration.
         * @return RealmConfiguration that can be used offline
         */
        fun forRecovery(canonicalPath: kotlin.String?): RealmConfiguration {
            return forRecovery(canonicalPath, null)
        }

        // Extract the full server path, minus the file name
        private fun getServerPath(user: User, serverUrl: URI): kotlin.String {
            // FIXME Add support for partion key
            // Current scheme is <rootDir>/<appId>/<userId>/default.realm or
            // Current scheme is <rootDir>/<appId>/<userId>/<hashedPartionKey>/default.realm
            return user.getApp().getConfiguration().getAppId()
                .toString() + "/" + user.getId() // TODO Check that it doesn't contain invalid filesystem chars
        }
    }

    init {
        this.user = user
        this.serverUrl = serverUrl
        this.errorHandler = errorHandler
        this.clientResetHandler = clientResetHandler
        this.deleteRealmOnLogout = deleteRealmOnLogout
        this.waitForInitialData = waitForInitialData
        this.initialDataTimeoutMillis = initialDataTimeoutMillis
        this.sessionStopPolicy = sessionStopPolicy
        urlPrefix = syncUrlPrefix
        this.clientResyncMode = clientResyncMode
        this.partitionValue = partitionValue
    }
}