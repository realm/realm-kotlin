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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.mongodb.AuthenticationProvider

internal class AuthenticationProviderImpl private constructor() {
    internal companion object {
        internal fun fromId(id: AuthProvider): AuthenticationProvider {
            for (value in AuthenticationProvider.values()) {
                if (fromNativeProvider(id) == value) {
                    return value
                }
            }
            error("Unknown authentication provider: $id")
        }

        private fun fromNativeProvider(provider: AuthProvider): AuthenticationProvider? {
            return when (provider) {
                // Collapse both anonymous providers under the same category to avoid exposing both to the public API
                AuthProvider.RLM_AUTH_PROVIDER_ANONYMOUS,
                AuthProvider.RLM_AUTH_PROVIDER_ANONYMOUS_NO_REUSE -> AuthenticationProvider.ANONYMOUS
                AuthProvider.RLM_AUTH_PROVIDER_FACEBOOK -> AuthenticationProvider.FACEBOOK
                AuthProvider.RLM_AUTH_PROVIDER_GOOGLE -> AuthenticationProvider.GOOGLE
                AuthProvider.RLM_AUTH_PROVIDER_APPLE -> AuthenticationProvider.APPLE
                AuthProvider.RLM_AUTH_PROVIDER_CUSTOM -> AuthenticationProvider.JWT
                AuthProvider.RLM_AUTH_PROVIDER_EMAIL_PASSWORD -> AuthenticationProvider.EMAIL_PASSWORD
                AuthProvider.RLM_AUTH_PROVIDER_USER_API_KEY -> AuthenticationProvider.API_KEY
                AuthProvider.RLM_AUTH_PROVIDER_FUNCTION -> AuthenticationProvider.CUSTOM_FUNCTION
                else -> null
            }
        }
    }
}
