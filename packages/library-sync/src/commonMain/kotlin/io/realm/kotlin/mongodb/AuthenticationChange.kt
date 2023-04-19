package io.realm.kotlin.mongodb

/**
 * TODO
 */
public data class AuthenticationChange(
    /**
     * TODO
     */
    public val type: Type,
    /**
     * TODO
     */
    public val user: User
) {

    /**
     * TODO
     */
    public enum class Type {
        /**
         * TODO
         */
        LOGGED_IN,
        /**
         * TODO
         */
        LOGGED_OUT
    }

    /**
     * TODO
     */
    public fun didLogIn(): Boolean = (type == Type.LOGGED_IN)

    /**
     * TODO
     */
    public fun didLogOut(): Boolean = (type == Type.LOGGED_OUT)
}