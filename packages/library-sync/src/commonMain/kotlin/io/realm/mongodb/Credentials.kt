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
 * Credentials represent a login with a given login provider.
 *
 * Credentials are used by the MongoDB Realm to verify the user and grant access. The credentials
 * are only useable if the corresponding authentication provider is enabled in the
 * [MongoDB Realm UI]{https://docs.mongodb.com/realm/authentication/providers/}
 */
interface Credentials {

    val authenticationProvider: AuthenticationProvider

    // TODO Consider adding asJson() like in Realm Java
    // fun asJson(): String

    companion object {
        /**
         * Creates credentials representing an anonymous user.
         *
         * @return credentials that can be used to log into MongoDB Realm using [App.login].
         */
        fun anonymous(): Credentials {
            return CredentialImpl(CredentialImpl.anonymous())
        }

        /**
         * Creates credentials representing a login using email and password.
         *
         * @param email    email of the user logging in.
         * @param password password of the user logging in.
         * @return credentials that can be used to log into MongoDB Realm using [App.login].
         */
        fun emailPassword(email: String, password: String): Credentials {
            return CredentialImpl(CredentialImpl.emailPassword(email, password))
        }
    }
}
