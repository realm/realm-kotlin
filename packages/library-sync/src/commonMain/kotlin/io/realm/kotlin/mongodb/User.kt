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

import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.ext.customDataAsBsonDocument
import io.realm.kotlin.mongodb.ext.profileAsBsonDocument
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

/**
 * A **user** holds the user's metadata and tokens for accessing App Services and Device Sync
 * functionality.
 *
 * User profile and custom data can be accessed using the extension functions [User.profileAsBsonDocument]
 * and [User.customDataAsBsonDocument].
 *
 * The user is used to configure synchronized realms with [SyncConfiguration.Builder].
 *
 * @see App.login
 * @see SyncConfiguration.Builder
 */
@Suppress("EqualsWithHashCodeExist") // Only overwriting equals to make docs available to user
public interface User {

    /**
     * The [App] this user is associated with.
     */
    public val app: App

    /**
     * Gives access to the [ApiKeyAuth] interface so that users can manage their API keys.
     */
    public val apiKeyAuth: ApiKeyAuth

    /**
     * The [State] this user is in.
     */
    public val state: State

    /**
     * The server id of the user.
     */
    public val id: String

    /**
     * Returns whether or not this user is still logged into the App Services Application.
     */
    public val loggedIn: Boolean

    /**
     * Returns a list of the user's identities as defined by the authentication providers enabled
     * for this user.
     *
     * @return the list of user credential identities.
     * @see UserIdentity
     */
    public val identities: List<UserIdentity>

    /**
     * Returns the current access token for the user.
     * If a user logs out, an empty access token is returned.
     */
    public val accessToken: String

    /**
     * Returns the current refresh token for the user.
     * If a user logs out an empty refresh token is returned.
     */
    public val refreshToken: String

    /**
     * Returns a unique identifier for the device the user logged in to.
     */
    public val deviceId: String

    /**
     * Returns a wrapper for invoking App Services Functions.
     *
     * [Atlas Functions documentation](https://www.mongodb.com/docs/atlas/app-services/functions/)
     */
    public val functions: Functions

    /**
     * Re-fetch custom user data from the Realm App.
     */
    public suspend fun refreshCustomData()

    // FIXME Review around user state
    /**
     * Log the user out of the Realm App. This will unregister them on the device and stop any
     * synchronization to and from the users' Realms. Any Realms owned by the user will
     * not be deleted from the device before [User.remove] is called.
     *
     * Once the Realm App confirms the logout, any registered [AuthenticationListener]
     * will be notified and user credentials will be deleted from this device.
     *
     * Logging out anonymous users will remove them immediately instead of marking them as
     * [User.State.LOGGED_OUT].
     *
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException if a failure occurred when
     * communicating with App Services. See [AppException] for details.
     * @throws IllegalStateException if a consumer listening to [App.authenticationChangeAsFlow]
     * is too slow consuming events.
     */
    // FIXME add references to allUsers and remove when ready
    //     * All other users will be marked as [User.State.LOGGED_OUT]
    //     * and will still be returned by [App.allUsers]. They can be removed completely by
    //     * calling [User.remove].asd
    // TODO Document how this method behave if offline
    public suspend fun logOut()

    /**
     * Removes the user and any Realms the user has from the device. No data is removed from the
     * server.
     *
     * If the user is logged in when calling this method, the user will be logged out before any
     * data is deleted.
     *
     * @return the user that was removed.
     * @throws IllegalStateException if the user was already removed.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException if a failure occurred when
     * communicating with App Services. See [AppException] for details.
     * @throws IllegalStateException if a consumer listening to [App.authenticationChangeAsFlow]
     * is too slow consuming events.
     */
    // TODO Document how this method behave if offline
    public suspend fun remove(): User

    /**
     * Permanently deletes this user from your Atlas App Services app.
     *
     * If the user was deleted successfully on Atlas, the user state will be set to
     * [State.REMOVED] and any local Realm files owned by the user will be deleted. If
     * the server request fails, the local state will not be modified.
     *
     * All user realms should be closed before calling this method.
     *
     * @throws IllegalStateException if the user was already removed or not logged in.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException if a failure occurred when
     * communicating with App Services. See [AppException] for details.
     * @throws IllegalStateException if a consumer listening to [App.authenticationChangeAsFlow]
     * is too slow consuming events.
     */
    public suspend fun delete()

    /**
     * Links the current user with a new user identity represented by the given credentials.
     *
     * Linking a user with more credentials mean the user can login with either of these
     * credentials. It also makes it possible to upgrade an anonymous user by linking it with e.g.
     * Email/Password credentials.
     *
     * Example:
     * ```
     * val app = new App("app-id")
     * val user: User = app.login(Credentials.anonymous());
     * user.linkCredentials(Credentials.emailPassword("email", "password"));
     * ```
     *
     * Note: It is not possible to link two existing Atlas App Service users. The provided
     * credentials must not be used by another user.
     *
     * @param credentials the credentials to link with the current user.
     * @return the [User] the credentials were linked to.
     *
     * @throws IllegalStateException if no user is currently logged in.
     * @throws io.realm.kotlin.mongodb.exceptions.CredentialsCannotBeLinkedException if linking the
     * two credentials are not supported.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException if a failure occurred when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun linkCredentials(credentials: Credentials): User

    /**
     * Get a [MongoClient] for accessing documents from App Service's _Data Source_.
     *
     * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
     * that supports the [Kotlin Serialization framework](https://github.com/Kotlin/kotlinx.serialization)
     * and handles serialization to and from classes marked with [Serializable]. Serialization of
     * realm objects and links have some caveats and requires special configuration. For full
     * details see [MongoClient].
     *
     * @param serviceName the name of the data service.
     * @param eJson the EJson serializer that the [MongoClient] should use to convert objects and
     * primary keys with. Will default to the app's [EJson] instance configured with
     * [AppConfiguration.Builder.ejson]. For details on configuration of serialization see
     * [MongoClient].
     * throws IllegalStateException if trying to obtain a [MongoClient] from a logged out [User].
     */
    @ExperimentalKBsonSerializerApi
    public fun mongoClient(serviceName: String, eJson: EJson? = null): MongoClient

    /**
     * Two Users are considered equal if they have the same user identity and are associated
     * with the same app.
     */
    override fun equals(other: Any?): Boolean

    /**
     * A user's potential states.
     */
    public enum class State {
        LOGGED_OUT,
        LOGGED_IN,
        REMOVED;
    }
}
