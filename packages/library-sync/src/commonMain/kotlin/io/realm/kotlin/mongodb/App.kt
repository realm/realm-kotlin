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

import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.mongodb.annotations.ExperimentalEdgeServerApi
import io.realm.kotlin.mongodb.auth.EmailPasswordAuth
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.mongodb.internal.AppImpl
import io.realm.kotlin.mongodb.sync.Sync
import kotlinx.coroutines.flow.Flow

/**
 * An **App** is the main client-side entry point for interacting with an **Atlas App Services
 * Application**.
 *
 * The **App** can be used to:
 * - Register and authenticate users.
 * - Synchronize data between the local device and Atlas using Device Sync.
 *
 * This can be done as shown below:
 *
 * ```
 *     class MyApplication {
 *         val app: App = App.create("<APP_ID>")
 *         val realm: Realm
 *
 *         init {
 *              realm = runBlocking {
 *                  val user = app.login(Credentials.anonymous())
 *                  val config = SyncConfiguration.Builder(
 *                      user = user,
 *                      partitionValue = "my-partition"
 *                      schema = setOf(YourRealmObject::class),
 *                  ).build()
 *
 *                  Realm.open(config)
 *              }
 *         }
 *     }
 * ```
 */
public interface App {

    public val configuration: AppConfiguration

    /**
     * Wrapper for interacting with functionality related to users either being created or logged
     * in using the [AuthenticationProvider.EMAIL_PASSWORD] identity provider.
     */
    public val emailPasswordAuth: EmailPasswordAuth

    /**
     * Returns the current user that is logged in and still valid.
     *
     * Users are invalidated when they log out or when their refresh tokens expire or are revoked.
     *
     * If two or more users are logged in, it is the last valid user that is returned by this
     * property.
     *
     * The value of this property will be `null` if no user is logged in or the user has expired.
     */
    public val currentUser: User?

    /**
     * Returns a Device Sync manager that control functionality across all open realms associated
     * with this app.
     */
    public val sync: Sync

    /**
     * Current base URL to communicate with App Services.
     */
    @ExperimentalEdgeServerApi
    public val baseUrl: String

    /**
     * Sets the App Services base url.
     *
     * *NOTE* Changing the URL would trigger a client reset.
     *
     * @param baseUrl The new App Services base url. If `null` it will be using the default value
     * ([AppConfiguration.DEFAULT_BASE_URL]).
     */
    @ExperimentalEdgeServerApi
    public suspend fun updateBaseUrl(baseUrl: String?)

    /**
     * Returns all known users that are either [User.State.LOGGED_IN] or [User.State.LOGGED_OUT].
     * Only users that at some point logged into this device will be returned.
     *
     * @return a list of users known locally.
     */
    public fun allUsers(): List<User>

    /**
     * Log in as a user with the given credentials associated with an authentication provider.
     *
     * @param credentials the credentials representing the type of login.
     * @return the logged in [User].
     * @throws InvalidCredentialsException if the provided credentials were not correct. Note, only
     * [AuthenticationProvider.EMAIL_PASSWORD], [AuthenticationProvider.API_KEY] and
     * [AuthenticationProvider.JWT] can throw this exception. Other authentication providers throw
     * an [AuthException] instead.
     * @throws AuthException if a problem occurred when logging in. See the exception message for
     * further details.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun login(credentials: Credentials): User

    /**
     * Create a [Flow] of [AuthenticationChange]-events to receive notifications of updates to all
     * app user authentication states: login, logout and removal.
     *
     * @return a [Flow] of authentication events for users associated with this app.
     */
    public fun authenticationChangeAsFlow(): Flow<AuthenticationChange>

    /**
     * Close the app instance and release all underlying resources.
     *
     * This class maintains a number of thread pools, these should normally run for the entire
     * lifetime of the application, but in some cases, like unit tests, it could be beneficial to
     * manually cleanup these resources.
     *
     * If not closed manually, these resources will be freed when the [App] instance is GC'ed.
     */
    public fun close()

    public companion object {
        /**
         * Create an [App] with default settings.
         * @param appId the App Services App ID.
         */
        public fun create(appId: String): App {
            Validation.checkEmpty(appId, "appId")
            // We cannot rewire this to create(appId, bundleId) and just have REPLACED_BY_IR here,
            // as these calls might be in a module where the compiler plugin hasn't been applied.
            // In that case we don't setup the correct bundle ID. If this is an issue we could maybe
            // just force users to apply our plugin.
            return create(AppConfiguration.Builder(appId).build())
        }

        /**
         * Create an [App] according to the given [AppConfiguration].
         *
         * @param configuration the configuration to use for this [App] instance.
         * @see AppConfiguration.Builder
         */
        public fun create(configuration: AppConfiguration): App =
            AppImpl(configuration as AppConfigurationImpl)
    }
}
