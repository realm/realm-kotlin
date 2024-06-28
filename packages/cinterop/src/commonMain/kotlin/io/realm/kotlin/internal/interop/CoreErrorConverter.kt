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

package io.realm.kotlin.internal.interop

import kotlin.jvm.JvmStatic

/**
 * Generic representation of a Realm-Core exception.
 */
object CoreErrorConverter {
    @JvmStatic
    @Suppress("UnusedPrivateMember")
    fun asThrowable(
        categoriesNativeValue: Int,
        errorCodeNativeValue: Int,
        messageNativeValue: String?,
        path: String?,
        userError: Throwable?
    ): Throwable {
        val categories: CategoryFlags = CategoryFlags(categoriesNativeValue)
        val errorCode: ErrorCode? = ErrorCode.of(errorCodeNativeValue)
        val message: String = "[$errorCode]: $messageNativeValue"

        return userError ?: when {
            ErrorCode.RLM_ERR_INDEX_OUT_OF_BOUNDS == errorCode ->
                IndexOutOfBoundsException(message)
            ErrorCode.RLM_ERR_INVALID_SCHEMA_CHANGE == errorCode ||
                    ErrorCode.RLM_ERR_INVALID_SCHEMA_VERSION == errorCode ->
                InvalidSchemaException(message)
            ErrorCategory.RLM_ERR_CAT_INVALID_ARG in categories && ErrorCategory.RLM_ERR_CAT_SYNC_ERROR !in categories -> {
                // Some sync errors flagged as both logical and illegal. In our case, we consider those
                // IllegalState, so discard them them here and let them fall through to the bottom case
                IllegalArgumentException(message)
            }
            ErrorCategory.RLM_ERR_CAT_LOGIC in categories || ErrorCategory.RLM_ERR_CAT_RUNTIME in categories ->
                IllegalStateException(message)
            else -> Error(message) // This can happen when propagating user level exceptions.
        }
    }
}
