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
import io.realm.kotlin.mongodb.auth.EmailPasswordAuth
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.mongodb.internal.AppImpl

/**
 * An **App** is the main client-side entry point for interacting with a **MongoDB Realm App**.
 *
 * The **App** can be used to:
 * - Register and authenticate users.
 * - Synchronize data between the local device and a backend Realm App with synchronized realms.
 *
 * To create an app that is linked with a remote **Realm App**, initialize Realm and configure the
 * **App** as shown below:
 *
 * ```
 *     class MyRealmAppClass {
 *         val app: App = App.create(<APP_ID>)
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
     * Returns all known users that are either [User.State.LOGGED_IN] or [User.State.LOGGED_OUT].
     * Only users that at some point logged into this device will be returned.
     *
     * @return a map of user identifiers and users known locally. User identifiers will match what
     * is returned by [User.identity].
     */
    public fun allUsers(): Map<String, User>

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

    public companion object {
        /**
         * Create an [App] with default settings.
         * @param appId the MongoDB Realm App ID.
         */
        public fun create(appId: String): App {
            Validation.checkEmpty(appId, "appId")
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
