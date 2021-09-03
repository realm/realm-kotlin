package io.realm.mongodb

import io.realm.mongodb.sync.SyncSession
import kotlin.collections.Map

/**
 * An [AppConfiguration] is used to setup a MongoDB Realm application.
 *
 * Instances of a AppConfiguration can only created by using the
 * [AppConfiguration.Builder] and calling its [AppConfiguration.Builder.build()] method.
 *
 * Configuring a App is only required if the default settings are not enough. Otherwise calling
 * `App.create("app-id")` is sufficient.
 */
interface AppConfiguration {
    companion object {
        /**
         * The default url for MongoDB Realm applications.
         * @see Builder.baseUrl
         */
        const val DEFAULT_BASE_URL = "https://realm.mongodb.com"

        /**
         * The default request timeout for network requests towards MongoDB Realm in seconds.
         * @see Builder.requestTimeout
         */
        const val DEFAULT_REQUEST_TIMEOUT: Long = 60

        /**
         * The default header name used to carry authorization data when making network requests
         * towards MongoDB Realm.
         */
        const val DEFAULT_AUTHORIZATION_HEADER_NAME = "Authorization"

        /**
         * Default BSON codec registry for encoding/decoding arguments and results to/from MongoDB Realm backend.
         *
         * This will encode/decode most primitive types, list and map types and BsonValues.
         *
         * @see [AppConfiguration.defaultCodecRegistry]
         * @see [AppConfiguration.Builder.codecRegistry]
         * @see [ValueCodecProvider]
         * @see BsonValueCodecProvider
         * @see IterableCodecProvider
         * @see MapCodecProvider
         * @see DocumentCodecProvider
         */
        val DEFAULT_BSON_CODEC_REGISTRY: CodecRegistry = TODO()

        /* CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders( // For primitive support
                ValueCodecProvider(),  // For BSONValue support
                BsonValueCodecProvider(),
                DocumentCodecProvider(),  // For list support
                IterableCodecProvider(),
                MapCodecProvider()
            )
        ) */

        /**
         * Default obfuscators for login requests used in a MongoDB Realm app.
         *
         * This map is needed to instantiate the default [HttpLogObfuscator], which will keep all
         * login-sensitive information from being shown in Logcat.
         *
         * This map's keys represent the different login identity providers which can be used to
         * authenticate against an app and the values are the concrete obfuscators used for that
         * provider.
         *
         * @see Credentials.Provider
         * @see RegexPatternObfuscator
         * @see ApiKeyObfuscator
         * @see TokenObfuscator
         * @see CustomFunctionObfuscator
         * @see EmailPasswordObfuscator
         * @see HttpLogObfuscator
         */
        val loginObfuscators: Map<String, RegexPatternObfuscator> = TODO()
            /* AppConfiguration.getLoginObfuscators() */
    }

    /**
     * The unique app id that identities the MongoDB Realm application.
     */
    val appId: String

    /**
     * The name used to describe the Realm application. This is only used as debug
     * information.
     */
    val appName: String

    /**
     * The version of this Realm application. This is only used as debug information.
     */
    val appVersion: String

    /**
     * The base url for this Realm application.
     */
    val baseUrl: URL

    /**
     * The encryption key, if any, that is used to encrypt Realm users meta data on this
     * device. If `null`, the data is not encrypted.
     */
    val encryptionKey: ByteArray?

    /**
     * The default timeout for network requests against the Realm application in
     * milliseconds.
     */
    val requestTimeoutMs: Long

    /**
     * The name of the header used to carry authentication data when making network
     * requests towards MongoDB Realm.
     */
    val authorizationHeaderName: String

    /**
     * Custom configured headers that will be sent alongside other headers when
     * making network requests towards MongoDB Realm.
     */
    val customRequestHeaders: Map<String, String>

    /**
     * The default error handler used by synced Realms if there are problems with their
     * [SyncSession].
     */
    val defaultErrorHandler: SyncSession.ErrorHandler

    /**
     * The default Client Reset handler used by synced Realms if there are problems with their
     * [SyncSession].
     */
    val defaultClientResetHandler: SyncSession.ClientResetHandler

    /**
     * The root folder containing all files and Realms used when synchronizing data
     * between the device and MongoDB Realm.
     */
    val syncRootDirectory: String // TODO Missing common java.io.File representation

    /**
     * The default codec registry used to encode and decode BSON arguments and results when
     * calling remote Realm [io.realm.mongodb.functions.Functions] and accessing a remote
     * [io.realm.mongodb.mongo.MongoDatabase].
     *
     * @return The default codec registry for the App.
     * @see .DEFAULT_BSON_CODEC_REGISTRY
     */
    val defaultCodecRegistry: CodecRegistry

    /**
     * Returns the [HttpLogObfuscator] used in the app, which keeps sensitive information in
     * HTTP requests from being displayed in the logcat.
     */
    val httpLogObfuscator: HttpLogObfuscator
}