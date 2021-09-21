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

package io.realm.mongodb.internal

/**
 * This enum contains the list of identity providers supported by MongoDB Realm.
 * All of these except [.EMAIL_PASSWORD] must be enabled manually on MongoDB Realm to
 * work.
 *
 * @see [Authentication Providers](https://docs.mongodb.com/realm/authentication/providers/)
 */
internal enum class Provider(id: String) {
    ANONYMOUS("anon-user"),
    // API_KEY("api-key"),  // same value as API_KEY as per OS specifications
    // APPLE("oauth2-apple"),
    // CUSTOM_FUNCTION("custom-function"),
    EMAIL_PASSWORD("local-userpass"),
    // FACEBOOK("oauth2-facebook"),
    // GOOGLE("oauth2-google"),
    // JWT("jwt"),
    UNKNOWN(""),
    ;

    /**
     * Return the string presentation of this identity provider.
     */
    val id = id

    companion object {
        /**
         * Create the identity provider from the ID string returned by MongoDB Realm.
         *
         * @param id the string identifier for the provider
         * @return the enum representing the provider or [.UNKNOWN] if no matching provider
         * was found.
         */
        fun fromId(id: String): Provider {
            for (value in values()) {
                if (value.id == id) {
                    return value
                }
            }
            return UNKNOWN
        }
    }
}
