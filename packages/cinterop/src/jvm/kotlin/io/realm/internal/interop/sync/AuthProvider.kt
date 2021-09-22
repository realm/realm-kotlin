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

package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_auth_provider_e

actual enum class AuthProvider(override val nativeValue: Int): NativeEnumerated {
    RLM_AUTH_PROVIDER_ANONYMOUS(realm_auth_provider_e.RLM_AUTH_PROVIDER_ANONYMOUS),
    RLM_AUTH_PROVIDER_FACEBOOK(realm_auth_provider_e.RLM_AUTH_PROVIDER_FACEBOOK),
    RLM_AUTH_PROVIDER_GOOGLE(realm_auth_provider_e.RLM_AUTH_PROVIDER_GOOGLE),
    RLM_AUTH_PROVIDER_APPLE(realm_auth_provider_e.RLM_AUTH_PROVIDER_APPLE),
    RLM_AUTH_PROVIDER_CUSTOM(realm_auth_provider_e.RLM_AUTH_PROVIDER_CUSTOM),
    RLM_AUTH_PROVIDER_USERNAME_PASSWORD(realm_auth_provider_e.RLM_AUTH_PROVIDER_USERNAME_PASSWORD),
    RLM_AUTH_PROVIDER_FUNCTION(realm_auth_provider_e.RLM_AUTH_PROVIDER_FUNCTION),
    RLM_AUTH_PROVIDER_USER_API_KEY(realm_auth_provider_e.RLM_AUTH_PROVIDER_USER_API_KEY),
    RLM_AUTH_PROVIDER_SERVER_API_KEY(realm_auth_provider_e.RLM_AUTH_PROVIDER_SERVER_API_KEY),
    ;

    companion object {
        // TODO Optimize
        fun of(id: Int): AuthProvider {
            for (value in AuthProvider.values()) {
                if (value.nativeValue == id) {
                    return value
                }
            }
            error("Unknown authentication provider $id")
        }
    }
}
