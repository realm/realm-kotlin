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

import io.realm.kotlin.mongodb.exceptions.AppException

/**
 * A **user** holds the user's metadata and tokens for accessing App Services and Device Sync
 * functionality.
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
     * The [State] this user is in.
     */
    public val state: State

    /**
     * The server id of the user.
     */
    public val identity: String

    /**
     * Returns whether or not this user is still logged into the MongoDB Realm App.
     */
    public val loggedIn: Boolean

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
     */
    // TODO Document how this method behave if offline
    public suspend fun remove(): User

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
