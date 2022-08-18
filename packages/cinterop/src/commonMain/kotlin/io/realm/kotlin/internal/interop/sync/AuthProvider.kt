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

package io.realm.kotlin.internal.interop.sync

/**
 * Wrapper for C-API `realm_auth_provider`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2615
 */
expect enum class AuthProvider {
    RLM_AUTH_PROVIDER_ANONYMOUS,
    RLM_AUTH_PROVIDER_ANONYMOUS_NO_REUSE,
    RLM_AUTH_PROVIDER_FACEBOOK,
    RLM_AUTH_PROVIDER_GOOGLE,
    RLM_AUTH_PROVIDER_APPLE,
    RLM_AUTH_PROVIDER_CUSTOM,
    RLM_AUTH_PROVIDER_EMAIL_PASSWORD,
    RLM_AUTH_PROVIDER_FUNCTION,
    RLM_AUTH_PROVIDER_USER_API_KEY,
    RLM_AUTH_PROVIDER_SERVER_API_KEY,
}
