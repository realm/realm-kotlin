package io.realm.mongodb.auth

/**
 * This enum contains the list of Google authentication types supported by MongoDB Realm.
 *
 * @see [Google Authentication](https://docs.mongodb.com/realm/authentication/google)
 */
enum class GoogleAuthType {
    AUTH_CODE, ID_TOKEN
}