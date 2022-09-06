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

import io.realm.kotlin.mongodb.internal.CredentialsImpl

/**
 * This enum contains the list of Google authentication types supported by App Services.
 *
 * **See:** [Google Authentication](https://docs.mongodb.com/realm/authentication/google)
 */
public enum class GoogleAuthType {
    /**
     * This signals that an Authentication Code OAuth 2.0 login flow is to be used.
     */
    AUTH_CODE,

    /**
     * This signals that an OpenID Connect OAuth 2.0 login flow is to be used.
     */
    ID_TOKEN
}

/**
 * Credentials represent a login with a given login provider.
 *
 * Credentials are used by an App Services Application to verify the user and grant access. The
 * credentials are only usable if the corresponding authentication provider is enabled in the
 * [App Services UI](https://docs.mongodb.com/realm/authentication/providers/).
 */
public interface Credentials {

    public val authenticationProvider: AuthenticationProvider

    public companion object {
        /**
         * Creates credentials representing an anonymous user.
         *
         * @param reuseExisting indicates whether anonymous users should be reused. Passing `true`
         * means that multiple calls to [App.login] with this function will return the same
         * anonymous user as long as that user hasn't logged out. If [reuseExisting] is `false`,
         * calls to [App.login] will create a new user on the server.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun anonymous(reuseExisting: Boolean = true): Credentials {
            return CredentialsImpl(CredentialsImpl.anonymous(reuseExisting))
        }

        /**
         * Creates credentials representing a login using email and password.
         *
         * @param email    email of the user logging in.
         * @param password password of the user logging in.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun emailPassword(email: String, password: String): Credentials {
            return CredentialsImpl(CredentialsImpl.emailPassword(email, password))
        }

        /**
         * Creates credentials representing a login using a user API key.
         *
         * @param key the user API key to use for login.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         * **See:** [API key authentication](https://www.mongodb.com/docs/realm/authentication/api-key/#api-key-authentication)
         */
        public fun apiKey(key: String): Credentials {
            return CredentialsImpl(CredentialsImpl.apiKey(key))
        }

        /**
         * Creates credentials representing a login using an Apple ID token.
         *
         * @param idToken the ID token generated when using your Apple login.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun apple(idToken: String): Credentials {
            return CredentialsImpl(CredentialsImpl.apple(idToken))
        }

        /**
         * Creates credentials representing a login using a Facebook access token.
         *
         * @param accessToken the access token returned when logging in to Facebook.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun facebook(accessToken: String): Credentials {
            return CredentialsImpl(CredentialsImpl.facebook(accessToken))
        }

        /**
         * Creates credentials representing a login using a Google access token of a given
         * [GoogleAuthType].
         *
         * @param token the ID Token or Auth Code returned when logging in to Google.
         * @param type the type of Google token used.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun google(token: String, type: GoogleAuthType): Credentials {
            return CredentialsImpl(CredentialsImpl.google(token, type))
        }

        /**
         * Creates credentials representing a login using a JWT Token. This token is normally
         * generated after a custom OAuth2 login flow.
         *
         * @param jwtToken the jwt token returned after a custom login to a another service.
         * @return a set of credentials that can be used to log into an App Services Application
         * using [App.login].
         */
        public fun jwt(jwtToken: String): Credentials {
            return CredentialsImpl(CredentialsImpl.jwt(jwtToken))
        }
    }
}
