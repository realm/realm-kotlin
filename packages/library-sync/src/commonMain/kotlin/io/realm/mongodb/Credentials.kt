/*
 * Copyright 2020 Realm Inc.
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

import io.realm.mongodb.auth.GoogleAuthType

/**
 * Credentials represent a login with a given login provider, and are used by MongoDB Realm to
 * verify the user and grant access. The [Provider.EMAIL_PASSWORD] provider is enabled
 * by default. All other providers must be enabled on MongoDB Realm to work.
 *
 * Note that users wanting to login using Email/Password must register first using
 * [io.realm.mongodb.auth.EmailPasswordAuth.registerUser].
 *
 * Credentials are used the following way:
 * ```
 * // Example
 * val app = App.defaultConfig("app-id")
 * val credentials = Credentials.emailPassword("email", "password");
 * scope.launch {
 *   val user = app.login(credentials)
 *
 * }
 *
 * User user = app.loginAsync(credentials, new App.Callback&lt;User&gt;() {
 * \@Override
 * public void onResult(Result&lt;User&gt; result) {
 * if (result.isSuccess() {
 * handleLogin(result.get());
 * } else {
 * handleError(result.getError());
 * }
 * }
 * ));
 * }
` *
</pre> *
 *
 * @see [Authentication Providers](https://docs.mongodb.com/realm/authentication/providers/)
 */
interface Credentials {

    /**
     * This enum contains the list of identity providers supported by MongoDB Realm.
     * All of these except [.EMAIL_PASSWORD] must be enabled manually on MongoDB Realm to
     * work.
     *
     * @see [Authentication Providers](https://docs.mongodb.com/realm/authentication/providers/)
     */
    enum class Provider(
        /**
         * Return the string presentation of this identity provider.
         */
        val id: String
    ) {
        ANONYMOUS("anon-user"),
        API_KEY("api-key"),  // same value as API_KEY as per OS specifications
        APPLE("oauth2-apple"),
        CUSTOM_FUNCTION("custom-function"),
        EMAIL_PASSWORD("local-userpass"),
        FACEBOOK("oauth2-facebook"),
        GOOGLE("oauth2-google"),
        JWT("jwt"),
        UNKNOWN("");

        companion object {
            /**
             * Create the identity provider from the ID string returned by MongoDB Realm.
             *
             * @param id the string identifier for the provider
             * @return the enum representing the provider or [.UNKNOWN] if no matching provider
             * was found.
             */
            internal fun fromId(id: String): Provider {
                for (value in values()) {
                    if (value.id == id) {
                        return value
                    }
                }
                return UNKNOWN
            }
        }
    }

    /**
     * The provider identifying the chosen credentials.
     */
    val identityProvider: Provider

    /**
     * Returns the credentials object serialized as a json string.
     *
     * @return a json serialized string of the credentials object.
     */
    fun asJson(): String


    companion object {
        /**
         * Creates credentials representing an anonymous user.
         *
         * Logging the user out again means that data is lost with no means of recovery
         * and it isn't possible to share the user details across devices.
         *
         * The anonymous user must be linked to another real user to preserve data after a log out.
         *
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun anonymous(): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using a user API key.
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param key the API key to use for login.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun apiKey(key: String): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using an Apple ID token.
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param idToken the ID token generated when using your Apple login.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun apple(idToken: String): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a remote function from MongoDB Realm using a
         * [Document] which will be parsed as an argument to the remote function, so the keys must
         * match the format and names the function expects.
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param arguments document containing the function arguments.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun customFunction(arguments: Document): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using email and password.
         *
         * @param email    email of the user logging in.
         * @param password password of the user logging in.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun emailPassword(email: String, password: String): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using a Facebook access token.
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param accessToken the access token returned when logging in to Facebook.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun facebook(accessToken: String): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using a Google access token of a given [GoogleAuthType].
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param token the access token returned when logging in to Google.
         * @param type the access token type
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun google(token: String, type: GoogleAuthType): Credentials {
            TODO()
        }

        /**
         * Creates credentials representing a login using a JWT Token. This token is normally generated
         * after a custom OAuth2 login flow.
         *
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param jwtToken the jwt token returned after a custom login to a another service.
         * @return a set of credentials that can be used to log into MongoDB Realm using
         * [App.loginAsync].
         */
        fun jwt(jwtToken: String): Credentials {
            TODO()
        }
    }
}