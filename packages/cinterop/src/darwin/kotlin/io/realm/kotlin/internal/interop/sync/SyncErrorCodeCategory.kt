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

import realm_wrapper.realm_sync_error_category

actual enum class SyncErrorCodeCategory(
    override val description: String,
    override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERROR_CATEGORY_CLIENT("Client", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CLIENT.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION("Connection", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CONNECTION.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_SESSION("Session", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SESSION.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM("System", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SYSTEM.value.toInt()),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN("Unknown", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_UNKNOWN.value.toInt());

    actual companion object {

        internal actual fun of(nativeValue: Int): SyncErrorCodeCategory? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
