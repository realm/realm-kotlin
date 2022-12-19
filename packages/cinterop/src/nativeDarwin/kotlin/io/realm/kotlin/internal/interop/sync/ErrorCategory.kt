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

import realm_wrapper.realm_error_category

/**
 * Error categories are composed of multiple categories in once, it is a flag property.
 */
actual enum class ErrorCategory(
    override val description: String,
    override val nativeValue: Int
) : CodeDescription {
    RLM_ERR_CAT_LOGIC("Logic", realm_error_category.RLM_ERR_CAT_LOGIC.value.toInt()),
    RLM_ERR_CAT_RUNTIME("Runtime", realm_error_category.RLM_ERR_CAT_RUNTIME.value.toInt()),
    RLM_ERR_CAT_INVALID_ARG("Invalid arg", realm_error_category.RLM_ERR_CAT_INVALID_ARG.value.toInt()),
    RLM_ERR_CAT_FILE_ACCESS(
        "File access",
        RLM_ERR_CAT_RUNTIME.nativeValue and realm_error_category.RLM_ERR_CAT_FILE_ACCESS.value.toInt()
    ),
    RLM_ERR_CAT_SYSTEM_ERROR(
        "System error",
        RLM_ERR_CAT_RUNTIME.nativeValue and realm_error_category.RLM_ERR_CAT_SYSTEM_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_APP_ERROR(
        "App error",
        RLM_ERR_CAT_RUNTIME.nativeValue and realm_error_category.RLM_ERR_CAT_APP_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_CLIENT_ERROR(
        "Client error",
        RLM_ERR_CAT_APP_ERROR.nativeValue and realm_error_category.RLM_ERR_CAT_CLIENT_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_JSON_ERROR(
        "Json error",
        RLM_ERR_CAT_APP_ERROR.nativeValue and realm_error_category.RLM_ERR_CAT_JSON_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_SERVICE_ERROR(
        "Service error",
        RLM_ERR_CAT_APP_ERROR.nativeValue and realm_error_category.RLM_ERR_CAT_SERVICE_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_HTTP_ERROR(
        "Http error",
        RLM_ERR_CAT_APP_ERROR.nativeValue and realm_error_category.RLM_ERR_CAT_HTTP_ERROR.value.toInt()
    ),
    RLM_ERR_CAT_CUSTOM_ERROR(
        "Custom error",
        RLM_ERR_CAT_APP_ERROR.nativeValue and realm_error_category.RLM_ERR_CAT_CUSTOM_ERROR.value.toInt()
    );

    actual companion object {

        internal actual fun of(nativeValue: Int): ErrorCategory? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
