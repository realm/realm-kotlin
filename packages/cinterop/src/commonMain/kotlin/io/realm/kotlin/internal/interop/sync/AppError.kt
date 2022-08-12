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

import kotlin.jvm.JvmStatic

interface ErrorCode {
    val description: String
}

interface CodeDescription {
    val nativeValue: Int
    val description: String?
}

typealias CategoryCodeDescription = CodeDescription
typealias ErrorCodeDescription = CodeDescription

data class UnknownCodeDescription(override val nativeValue: Int) : CodeDescription {
    override val description: String? = null
}

/**
 * Wrapper for C-API `realm_app_error`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2638
 */
data class AppError internal constructor(
    val category: CategoryCodeDescription,
    val error: ErrorCodeDescription,
    val httpStatusCode: Int, // If the category is HTTP, this is equal to errorCode
    val message: String?,
    val linkToServerLog: String?
) {
    companion object {
        @JvmStatic
        fun newInstance(
            categoryCode: Int,
            errorCode: Int,
            httpStatusCode: Int,
            message: String?,
            linkToServerLog: String?
        ): AppError {
            val category = AppErrorCategory.of(categoryCode) ?: UnknownCodeDescription(categoryCode)

            val error: ErrorCodeDescription = when (category) {
                AppErrorCategory.RLM_APP_ERROR_CATEGORY_CLIENT -> ClientErrorCode.of(errorCode)
                AppErrorCategory.RLM_APP_ERROR_CATEGORY_JSON -> JsonErrorCode.of(errorCode)
                AppErrorCategory.RLM_APP_ERROR_CATEGORY_SERVICE -> ServiceErrorCode.of(errorCode)
                // AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM, // no mapping available
                // AppErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP, // no mapping available
                else -> null
            } ?: UnknownCodeDescription(errorCode)

            return AppError(
                category,
                error,
                httpStatusCode,
                message,
                linkToServerLog
            )
        }
    }
}
