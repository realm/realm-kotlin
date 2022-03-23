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

import io.realm.mongodb.internal.CredentialImpl

/**
 * This enum contains the list of Google authentication types supported by MongoDB Realm.
 *
 * @see [Google Authentication](https://docs.mongodb.com/realm/authentication/google)
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
 * Credentials are used by MongoDB Realm to verify the user and grant access. The credentials
 * are only usable if the corresponding authentication provider is enabled in the
 * [MongoDB Realm UI](https://docs.mongodb.com/realm/authentication/providers/).
 */
public interface Credentials {

    public val authenticationProvider: AuthenticationProvider

    public companion object {
        /**
         * Creates credentials representing an anonymous user.
         *
         * @return credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun anonymous(): Credentials {
            return CredentialImpl(CredentialImpl.anonymous())
        }

        /**
         * Creates credentials representing a login using email and password.
         *
         * @param email    email of the user logging in.
         * @param password password of the user logging in.
         * @return credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun emailPassword(email: String, password: String): Credentials {
            return CredentialImpl(CredentialImpl.emailPassword(email, password))
        }

        /**
         * Creates credentials representing a login using a user API key.
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param key the API key to use for login.
         * @return a set of credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun apiKey(key: String): Credentials {
            return CredentialImpl(CredentialImpl.apiKey(key))
        }

        /**
         * Creates credentials representing a login using an Apple ID token.
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param idToken the ID token generated when using your Apple login.
         * @return a set of credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun apple(idToken: String): Credentials {
            return CredentialImpl(CredentialImpl.apple(idToken))
        }

        /**
         * Creates credentials representing a login using a Facebook access token.
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param accessToken the access token returned when logging in to Facebook.
         * @return a set of credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun facebook(accessToken: String): Credentials {
            return CredentialImpl(CredentialImpl.facebook(accessToken))
        }

        /**
         * Creates credentials representing a login using a Google access token of a given
         * [GoogleAuthType].
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param token the ID Token or Auth Code returned when logging in to Google.
         * @param type the type of Google token used.
         * @return a set of credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun google(token: String, type: GoogleAuthType): Credentials {
            return CredentialImpl(CredentialImpl.google(token, type))
        }

        /**
         * Creates credentials representing a login using a JWT Token. This token is normally
         * generated after a custom OAuth2 login flow.
         *
         * This provider must be enabled on MongoDB Realm to work.
         *
         * @param jwtToken the jwt token returned after a custom login to a another service.
         * @return a set of credentials that can be used to log into MongoDB Realm using [App.login].
         */
        public fun jwt(jwtToken: String): Credentials {
            return CredentialImpl(CredentialImpl.jwt(jwtToken))
        }
    }
}
