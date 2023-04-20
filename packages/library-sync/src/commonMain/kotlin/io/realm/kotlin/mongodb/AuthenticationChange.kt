package io.realm.kotlin.mongodb

/**
 * TODO
 */
public sealed interface AuthenticationChange {
    /**
     * TODO
     */
    public val user: User
    /**
     * TODO
     */
    public fun didLogIn(): Boolean
    /**
     * TODO
     */
    public fun didLogOut(): Boolean
}

/**
 * TODO
 */
public interface LoggedIn : AuthenticationChange

/**
 * TODO
 */
public interface LoggedOut : AuthenticationChange

/**
 * TODO
 */
public interface Removed : AuthenticationChange
