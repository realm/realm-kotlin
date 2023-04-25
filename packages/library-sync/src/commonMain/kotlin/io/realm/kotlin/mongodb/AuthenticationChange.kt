/*
 * Copyright 2023 Realm Inc.
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

import kotlinx.coroutines.flow.Flow

/**
 * This sealed class describe the possible events that can be observed on the [Flow] created by
 * calling [App.authenticationChangeAsFlow].
 *
 * The specific states are represented by these subclasses [LoggedIn], [LoggedOut] and
 * [Removed].
 *
 * Changes can thus be consumed this way:
 *
 * ```
 * app.authenticationChangeAsFlow().collect { change: AuthenticationChange ->
 *       when(change) {
 *          is LoggedIn -> handleLogin(change.user)
 *          is LoggedOut -> handleLogOut(change.user)
 *          is Removed -> handleRemove(change.user)
 *       }
 *   }
 * ```
 */
public sealed interface AuthenticationChange {
    /**
     * A reference to the [User] this event happened to.
     *
     * *Warning:* This is the live user object, so the [User.state] might have diverged from the
     * event it is associated with, i.e. if a users logs out and back in while the event is
     * propagating, the state of the user might be [User.State.LOGGED_IN], even though it was
     * reported as a [LoggedOut] event.
     */
    public val user: User
}

/**
 * Event emitted when a user logs into the app.
 */
public interface LoggedIn : AuthenticationChange

/**
 * Event emitted when a user is logged out.
 */
public interface LoggedOut : AuthenticationChange

/**
 * Event emitted when a user is removed, which also logs them out.
 */
public interface Removed : AuthenticationChange
