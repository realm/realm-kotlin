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

expect enum class AuthProvider {
    RLM_AUTH_PROVIDER_ANONYMOUS,
    RLM_AUTH_PROVIDER_FACEBOOK,
    RLM_AUTH_PROVIDER_GOOGLE,
    RLM_AUTH_PROVIDER_APPLE,
    RLM_AUTH_PROVIDER_CUSTOM,
    RLM_AUTH_PROVIDER_USERNAME_PASSWORD,
    RLM_AUTH_PROVIDER_FUNCTION,
    RLM_AUTH_PROVIDER_USER_API_KEY,
    RLM_AUTH_PROVIDER_SERVER_API_KEY,
}
