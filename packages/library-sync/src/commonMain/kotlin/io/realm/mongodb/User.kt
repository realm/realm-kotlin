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
package io.realm.mongodb

import io.realm.mongodb.auth.ApiKeyAuth
import io.realm.mongodb.functions.Functions
import io.realm.mongodb.push.Push
import org.bson.Document
import java.lang.IllegalStateException
import java.lang.Void
import java.util.ArrayList
import kotlin.jvm.Synchronized

/**
 * A *user* holds the user's meta data and tokens for accessing Realm App functionality.
 *
 *
 * The user is used to configure Synchronized Realms and gives access to calling Realm App *Functions*
 * through [Functions] and accessing remote Realm App *Mongo Databases* through a
 * [MongoClient].
 *
 * @see App.login
 * @see io.realm.mongodb.sync.SyncConfiguration.Builder.Builder
 */
interface User {

    /**
     * The user's potential states.
     */
    enum class State(val key: Byte) {
        LOGGED_IN(0 /*OsSyncUser.STATE_LOGGED_IN*/),
        REMOVED(1 /*OsSyncUser.STATE_REMOVED*/),
        LOGGED_OUT( 2 /*OsSyncUser.STATE_LOGGED_OUT*/);
    }

    /**
     * The [App] this user is associated with.
     */
    val app: App

    /**
     * The server id of the user.
     */
    val id: String

    /**
     * The profile for this user.
     */
    val profile: UserProfile

    /**
     * A list of the user's identities.
     * @see UserIdentity
     */
    val identities: List<UserIdentity>

    /**
     * The provider type used to log the user in.
     */
    val providerType: Credentials.Provider

    /**
     * The current access token for the user.
     */
    var accessToken: String

    /**
     * The current refresh token for the user.
     */
    val refreshToken: String

    /**
     * A unique identifier for the device the user logged in to.
     */
    val deviceId: String

    /**
     * Returns the [State] the user is in.
     */
    var state: State

    /**
     * Return the custom user data associated with the user in the Realm App.
     *
     * The data is only refreshed when the user's access token is refreshed or when explicitly
     * calling [.refreshCustomData].
     *
     * @return The custom user data associated with the user.
     */
    var customData: Document

    /**
     * Re-fetch custom user data from the Realm App.
     *
     * @return The updated custom user data associated with the user.
     * @throws AppException if the request failed in some way.
     */
    suspend fun refreshCustomData(): Document

    /**
     * Returns true if the user is currently logged in.
     * Returns whether or not this user is still logged into the MongoDB Realm App.
     *
     * @return `true` if still logged in, `false` if not.
     */
    fun isLoggedIn(): Boolean

    /**
     * Links the current user with a new user identity represented by the given credentials.
     *
     *
     * Linking a user with more credentials, mean the user can login either of these credentials.
     * It also makes it possible to "upgrade" an anonymous user by linking it with e.g.
     * Email/Password credentials.
     * <pre>
     * `// Example
     * App app = new App("app-id")
     * User user = app.login(Credentials.anonymous());
     * user.linkCredentials(Credentials.emailPassword("email", "password"));
    ` *
    </pre> *
     *
     *
     * Note: It is not possible to link two existing users of MongoDB Realm. The provided credentials
     * must not have been used by another user.
     *
     * @param credentials the credentials to link with the current user.
     * @return the [User] the credentials were linked to.
     *
     * @throws IllegalStateException if no user is currently logged in.
     */
    suspend fun linkCredentials(credentials: Credentials): User

    /**
     * Calling this will remove the user and any Realms the user has from the device. No data
     * is removed from the server.
     *
     * If the user is logged in when calling this method, the user is logged out before any data
     * is deleted.
     *
     * @throws AppException if an error occurred while trying to remove the user.
     * @return the user that was removed.
     */
    suspend fun remove(): User

    /**
     * Log the user out of the Realm App. This will unregister them on the device and stop any
     * synchronization to and from the users' Realms. Any Realms owned by the user will
     * not be deleted from the device before [User.remove] is called.
     *
     *
     *
     * Once the Realm App has confirmed the logout any registered [AuthenticationListener]
     * will be notified and user credentials will be deleted from this device.
     *
     *
     * Logging out anonymous users will remove them immediately instead of marking them as
     * [State.LOGGED_OUT].
     *
     *
     * All other users will be marked as [State.LOGGED_OUT]
     * and will still be returned by [App.allUsers]. They can be removed completely by
     * calling [User.remove].
     *
     * @throws AppException if an error occurred while trying to log the user out of the Realm
     * App.
     */
    suspend fun logOut()

    /**
     * Returns a wrapper for managing API keys controlled by the current user.
     *
     * @return wrapper for managing API keys controlled by the current user.
     * @throws IllegalStateException if no user is currently logged in.
     */
    val apiKeys: ApiKeyAuth

    /**
     * Returns a <i>functions</i> manager for invoking MongoDB Realm Functions.
     * <p>
     * This will use the associated app's default codec registry to encode and decode arguments and
     * results.
     *
     * @see Functions
     */
    val functions: Functions

        /**
     * Returns a *functions* manager for invoking Realm Functions with custom
     * codec registry for encoding and decoding arguments and results.
     *
     * @param codecRegistry The codec registry to use for encoding and decoding arguments and results
     * towards the remote Realm App.
     * @see Functions
     */
    fun getFunctions(codecRegistry: CodecRegistry?): Functions

    /**
     * Returns the [Push] instance for managing push notification registrations.
     *
     * @param serviceName the service name used to connect to the server.
     */
    val push: Push

    /**
     * Returns a [MongoClient] instance for accessing documents in the database.
     *
     * @param serviceName the service name used to connect to the server.
     */
    fun getMongoClient(serviceName: String): MongoClient

}