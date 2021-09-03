package io.realm.mongodb

/**
 * Interface describing events related to Users and their authentication
 */
interface AuthenticationListener {
    /**
     * A user was logged into the Object Server
     *
     * @param user [User] that is now logged in.
     */
    fun loggedIn(user: User?)

    /**
     * A user was successfully logged out from the Object Server.
     *
     * @param user [User] that was successfully logged out.
     */
    fun loggedOut(user: User?)
}