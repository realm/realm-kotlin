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
@Suppress("EqualsWithHashCodeExist") // Only overwriting equals to make docs available to user
interface User {

    /**
     * The [App] this user is associated with.
     */
    val app: App

    /**
     * The [State] this user is in.
     */
    val state: State

    /**
     * The server id of the user.
     */
    val identity: String

    /**
     * Returns whether or not this user is still logged into the MongoDB Realm App.
     */
    val loggedIn: Boolean

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
     * @throws AppException if an error occurred while trying to log the user out of the Realm
     * App.
     */
    // FIXME add references to allUsers and remove when ready
    //     * All other users will be marked as [User.State.LOGGED_OUT]
    //     * and will still be returned by [App.allUsers]. They can be removed completely by
    //     * calling [User.remove].asd

    suspend fun logOut()

    /**
     * Two Users are considered equal if they have the same user identity and are associated
     * with the same app.
     */
    override fun equals(other: Any?): Boolean

    /**
     * A user's potential states.
     */
    enum class State {
        LOGGED_OUT,
        LOGGED_IN,
        REMOVED;
    }
}
