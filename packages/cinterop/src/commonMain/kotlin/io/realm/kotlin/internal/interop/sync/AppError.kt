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

import io.realm.kotlin.internal.interop.CodeDescription
import io.realm.kotlin.internal.interop.ErrorCategory
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.UnknownCodeDescription
import kotlin.jvm.JvmStatic

/**
 * Wrapper for C-API `realm_app_error`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2638
 */
data class AppError internal constructor(
    val categoryFlags: Int,
    val code: CodeDescription,
    val httpStatusCode: Int, // If the category is HTTP, this is equal to errorCode
    val message: String?,
    val linkToServerLog: String?
) {
    companion object {
        @JvmStatic
        fun newInstance(
            categoryFlags: Int,
            errorCode: Int,
            httpStatusCode: Int,
            message: String?,
            linkToServerLog: String?
        ): AppError {
            val code = ErrorCode.of(errorCode) ?: UnknownCodeDescription(errorCode)

            return AppError(
                categoryFlags,
                code,
                httpStatusCode,
                message,
                linkToServerLog
            )
        }
    }

    /**
     * This method allows to check whether a error categories value contains a category or not.
     *
     * Core defines app categories as flag based values.
     *
     * Any App category is also a [ErrorCategory.RLM_ERR_CAT_RUNTIME] and [ErrorCategory.RLM_ERR_CAT_APP_ERROR].
     */
    operator fun contains(flag: ErrorCategory): Boolean =
        this.categoryFlags and flag.nativeValue != 0
}
