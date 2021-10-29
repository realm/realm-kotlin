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

/**
 * A **user** holds the user's metadata and tokens for accessing Realm App functionality.
 *
 * The user is used to configure synchronized realms with [SyncConfiguration.Builder].
 *
 * @see App.login
 * @see SyncConfiguration.Builder
 */
interface User {

    /**
     * The [App] this user is associated with.
     */
    val app: App

    // TODO Property or method? Can maybe fail, but we could also cache the return value?
    fun identity(): String
    fun isLoggedIn(): Boolean

    // FIXME Review around user state
    /**
     * Log the user out of the Realm App. This will unregister them on the device and stop any
     * synchronization to and from the users' Realms. Any Realms owned by the user will
     * not be deleted from the device before [User.remove] is called.
     *
     * Once the Realm App has confirmed the logout any registered [AuthenticationListener]
     * will be notified and user credentials will be deleted from this device.
     *
     * Logging out anonymous users will remove them immediately instead of marking them as
     * [User.State.LOGGED_OUT].
     *
     * All other users will be marked as [User.State.LOGGED_OUT]
     * and will still be returned by [App.allUsers]. They can be removed completely by
     * calling [User.remove].
     *
     * @throws AppException if an error occurred while trying to log the user out of the Realm
     * App.
     */
    suspend fun logOut()

    /**
     * Two Users are considered equal if they have the same user identity and are associated
     * with the same app.
     */
    override fun equals(o: Any?): Boolean
}

