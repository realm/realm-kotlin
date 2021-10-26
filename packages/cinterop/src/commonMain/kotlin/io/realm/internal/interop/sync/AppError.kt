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

sealed class AppError(
    val errorCategory: ErrorCategory,
    val message: String,
    val serverLogUrl: String
) {
    companion object {
        fun createAppError(errorCategory: Int, errorCode: Int, message: String, serverLogUrl: String): AppError {
            return when (ErrorCategory.from(errorCategory)) {
                ErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP -> TODO()
                ErrorCategory.RLM_APP_ERROR_CATEGORY_JSON -> TODO()
                ErrorCategory.RLM_APP_ERROR_CATEGORY_CLIENT -> TODO()
                ErrorCategory.RLM_APP_ERROR_CATEGORY_SERVICE ->
                    ServiceAppError(ServiceErrorCode.from(errorCode), message, serverLogUrl)
                ErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM -> TODO()
                else -> TODO()
            }
        }
    }
}

class ServiceAppError(
    val errorCode: ServiceErrorCode,
    message: String,
    serverLogUrl: String
) : AppError(ErrorCategory.RLM_APP_ERROR_CATEGORY_SERVICE, message, serverLogUrl)


