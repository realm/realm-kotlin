/*
 * Copyright 2022 Realm Inc.
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

actual enum class ClientErrorCode(
    override val description: String,
    override val nativeValue: Int
) : ErrorCodeDescription {
    RLM_APP_ERR_CLIENT_USER_NOT_FOUND("UserNotFound", realm_wrapper.RLM_APP_ERR_CLIENT_USER_NOT_FOUND.toInt()),
    RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN("UserNotLoggedIn", realm_wrapper.RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN.toInt()),
    RLM_APP_ERR_CLIENT_APP_DEALLOCATED("AppDeallocated", realm_wrapper.RLM_APP_ERR_CLIENT_APP_DEALLOCATED.toInt());

    actual companion object {
        internal actual fun of(nativeValue: Int): ClientErrorCode? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
