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

/**
 * Wrapper for C-API `realm_sync_error_code`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3306
 */
data class SyncErrorCode internal constructor(
    val category: SyncErrorCodeCategory?,
    val error: ErrorCode?,
    val categoryCode: Int,
    val errorCode: Int,
    val message: String
) {
    companion object {
        fun newInstance(
            categoryCode: Int,
            errorCode: Int,
            message: String
        ): SyncErrorCode {
            val category = SyncErrorCodeCategory.of(categoryCode)

            val error: ErrorCode? = when (category) {
                SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT -> ProtocolClientErrorCode.of(errorCode)
                SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CONNECTION -> ProtocolConnectionErrorCode.of(errorCode)
                SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SESSION -> ProtocolSessionErrorCode.of(errorCode)
                // SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SYSTEM -> // no mapping available
                // SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN -> // no mapping available
                else -> null
            }

            return SyncErrorCode(
                category,
                error,
                categoryCode,
                errorCode,
                message
            )
        }
    }
}
