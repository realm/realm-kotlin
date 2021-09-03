package io.realm.mongodb

import io.realm.mongodb.auth.EmailPasswordAuth
import io.realm.mongodb.sync.Sync
import java.util.HashMap

/**
 * An _App_ is the main client-side entry point for interacting with a _MongoDB Realm App_.
 *
 * The _App_ can be used to:
 *  * Register uses and perform various user-related operations through authentication providers
 *   ({@link io.realm.mongodb.auth.ApiKeyAuth}, {@link EmailPasswordAuthImpl})
 *  * Synchronize data between the local device and a remote Realm App with Synchronized Realms
 *  * Invoke Realm App functions with {@link Functions}
 *  * Access remote data from MongoDB databases with a {@link io.realm.mongodb.mongo.MongoClient}
 *
 * To create an app that is linked with a remote _Realm App_ initialize Realm and configure the
 * _App_ as shown below:
 *
 * ```
 *    class MyApplication extends Application {
 *
 *         App APP; // The App instance should be a global singleton
 *
 *         \@Override
 *         public void onCreate() {
 *             super.onCreate();
 *
 *             Realm.init(this);
 *
 *             AppConfiguration appConfiguration = new AppConfiguration.Builder(BuildConfig.MONGODB_REALM_APP_ID)
 *                     .appName(BuildConfig.VERSION_NAME)
 *                     .appVersion(Integer.toString(BuildConfig.VERSION_CODE))
 *                     .build();
 *
 *             APP = new App(appConfiguration);
 *         }
 *
 *     }
 * ```
 *
 * After configuring the _App_ you can start managing users, configure Synchronized Realms,
 * call remote Realm Functions and access remote data through Mongo Collections. The examples below
 * show the synchronized APIs which cannot be used from the main thread. For the equivalent
 * asynchronous counterparts. The example project in please see
 * https://github.com/realm/realm-java/tree/v10/examples/mongoDbRealmExample.
 *
 * To register a new user and/or login with an existing user do as shown below:
 * ```
 *     // Register new user
 *     APP.getEmailPassword().registerUser(username, password);
 *
 *     // Login with existing user
 *     User user = APP.login(Credentials.emailPassword(username, password))
 * ```
 *
 * With an authorized user you can synchronize data between the local device and the remote Realm
 * App by opening a Realm with a {@link io.realm.mongodb.sync.SyncConfiguration} as indicated below:
 * ```
 *     SyncConfiguration syncConfiguration = new SyncConfiguration.Builder(user, "&lt;partition value&gt;")
 *              .build();
 *
 *     Realm instance = Realm.getInstance(syncConfiguration);
 *     SyncSession session = APP.getSync().getSession(syncConfiguration);
 *
 *     instance.executeTransaction(realm -&gt; {
 *         realm.insert(...);
 *     });
 *     session.uploadAllLocalChanges();
 *     instance.close();
 * ```
 *
 * You can call remote Realm functions as shown below:
 * ```
 *     Functions functions = user.getFunctions();
 *     Integer sum = functions.callFunction("sum", Arrays.asList(1, 2, 3, 4), Integer.class);
 * ```
 *
 * And access collections from the remote Realm App as shown here:
 * ```
 *     MongoClient client = user.getMongoClient(SERVICE_NAME)
 *     MongoDatabase database = client.getDatabase(DATABASE_NAME)
 *     MongoCollection&lt;DocumentT&gt; collection = database.getCollection(COLLECTION_NAME);
 *     Long count = collection.count().get()
 * ```
 *
 * @see AppConfiguration.Builder
 * @see EmailPasswordAuth
 * @see io.realm.mongodb.sync.SyncConfiguration
 * @see User#getFunctions()
 * @see User#getMongoClient(String)
 */
interface App {
    companion object {
        fun create(configuration: AppConfiguration) {
            TODO()
        }
        fun create(appId: String) {
            TODO()
        }
    }
    /**
     * Returns the configuration object for this app.
     */
    val configuration: AppConfiguration

    /**
     * Returns a wrapper for interacting with functionality related to users either being created or
     * logged in using the [Credentials.Provider.EMAIL_PASSWORD] identity provider.
     *
     * @return wrapper for interacting with the [Credentials.Provider.EMAIL_PASSWORD] identity provider.
     */
    var emailPasswordAuth: EmailPasswordAuth

    /**
     * Returns the *Sync* instance managing the ongoing *Realm Sync* sessions
     * synchronizing data between the local and the remote *Realm App* associated with this app.
     *
     * @return the *Sync* instance associated with this *App*.
     */
    val sync: Sync

    /**
     * Returns the current user that is logged in and still valid.
     *
     *
     * A user is invalidated when he/she logs out or the user's refresh token expires or is revoked.
     *
     *
     * If two or more users are logged in, it is the last valid user that is returned by this method.
     *
     * @return current [User] that has logged in and is still valid. `null` if no
     * user is logged in or the user has expired.
     */
    fun currentUser(): User?

    /**
     * Returns all known users that are either [User.State.LOGGED_IN] or
     * [User.State.LOGGED_OUT].
     *
     *
     * Only users that at some point logged into this device will be returned.
     *
     * @return a map of user identifiers and users known locally.
     */
    fun allUsers(): Map<String, User>

    /**
     * Switch current user.
     *
     *
     * The current user is the user returned by [.currentUser].
     *
     * @param user the new current user.
     * @throws IllegalArgumentException if the user is is not [User.State.LOGGED_IN].
     */
    fun switchUser(user: User): User?

    /**
     * Removes a users credentials from this device. If the user was currently logged in, they
     * will be logged out as part of the process. This is only a local change and does not
     * affect the user state on the server.
     *
     * @param user to remove
     * @return user that was removed.
     * @throws AppException if called from the UI thread or if the user was logged in, but
     * could not be logged out.
     */
    suspend fun removeUser(user: User): User

    /**
     * Logs in as a user with the given credentials associated with an authentication provider.
     *
     *
     * The user who logs in becomes the current user. Other App functionality acts on behalf of
     * the current user.
     *
     *
     * If there was already a current user, that user is still logged in and can be found in the
     * list returned by [.allUsers].
     *
     *
     * It is also possible to switch between which user is considered the current user by using
     * [.switchUser].
     *
     * @param credentials the credentials representing the type of login.
     * @return a [User] representing the logged in user.
     * @throws AppException if the user could not be logged in.
     */
    suspend fun login(credentials: Credentials): Result<User>

    /**
     * Sets a global authentication listener that will be notified about User events like
     * login and logout.
     *
     *
     * Callbacks to authentication listeners will happen on the UI thread.
     *
     * @param listener listener to register.
     * @throws IllegalArgumentException if `listener` is `null`.
     */
    fun addAuthenticationListener(listener: AuthenticationListener): Cancellable
}


