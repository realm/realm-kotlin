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

package io.realm.internal.interop.sync

import realm_wrapper.realm_sync_error_category

actual enum class SyncErrorCodeCategory(actual val description: String, val nativeValue: realm_sync_error_category) {
    RLM_SYNC_ERROR_CATEGORY_CLIENT("Client", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION("Connection", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    RLM_SYNC_ERROR_CATEGORY_SESSION("Session", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SESSION),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM("System", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN("Unknown", realm_sync_error_category.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    actual companion object {

        actual fun fromInt(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue.value.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category: $nativeValue")
        }

        fun of(nativeValue: realm_sync_error_category): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category: $nativeValue")
        }
    }
}
