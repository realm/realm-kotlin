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
 * app.authenticationChangeAsFlow().asFlow().collect { change: AuthenticationChange ->
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
     * A reference to the [User] that the event happened to.
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
