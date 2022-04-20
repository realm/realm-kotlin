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

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_sync_error_category_e

actual enum class SyncErrorCodeCategory(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_ERROR_CATEGORY_CLIENT(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CLIENT),
    RLM_SYNC_ERROR_CATEGORY_CONNECTION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_CONNECTION),
    RLM_SYNC_ERROR_CATEGORY_SESSION(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SESSION),
    RLM_SYNC_ERROR_CATEGORY_SYSTEM(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_SYSTEM),
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN(realm_sync_error_category_e.RLM_SYNC_ERROR_CATEGORY_UNKNOWN);

    actual companion object {
        actual fun fromInt(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category value: $nativeValue")
        }

        @JvmStatic
        fun of(nativeValue: Int): SyncErrorCodeCategory {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown sync error code category value: $nativeValue")
        }
    }
}
