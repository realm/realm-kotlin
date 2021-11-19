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

import io.realm.internal.interop.sync.AuthProvider

/**
 * This enum contains the list of authentication providers supported by MongoDB Realm.
 *
 * The authentication provider must be enabled in MongoDB Realm App to work.
 *
 * @see [Authentication Providers](https://docs.mongodb.com/realm/authentication/providers/)
 */
enum class AuthenticationProvider(id: AuthProvider) {
    ANONYMOUS(AuthProvider.RLM_AUTH_PROVIDER_ANONYMOUS),
    // API_KEY("api-key"),  // same value as API_KEY as per OS specifications
    // APPLE("oauth2-apple"),
    // CUSTOM_FUNCTION("custom-function"),
    EMAIL_PASSWORD(AuthProvider.RLM_AUTH_PROVIDER_EMAIL_PASSWORD),
    // FACEBOOK("oauth2-facebook"),
    // GOOGLE("oauth2-google"),
    // JWT("jwt"),
    // UNKNOWN(""),
    ;

    internal val id: AuthProvider = id
}
