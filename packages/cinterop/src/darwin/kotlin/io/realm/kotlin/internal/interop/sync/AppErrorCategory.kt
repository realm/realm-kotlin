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

import realm_wrapper.realm_app_error_category

actual enum class AppErrorCategory(
    override val description: String,
    override val nativeValue: Int
) : CategoryCodeDescription {
    RLM_APP_ERROR_CATEGORY_HTTP("Http", realm_app_error_category.RLM_APP_ERROR_CATEGORY_HTTP.value.toInt()),
    RLM_APP_ERROR_CATEGORY_JSON("Json", realm_app_error_category.RLM_APP_ERROR_CATEGORY_JSON.value.toInt()),
    RLM_APP_ERROR_CATEGORY_CLIENT("Client", realm_app_error_category.RLM_APP_ERROR_CATEGORY_CLIENT.value.toInt()),
    RLM_APP_ERROR_CATEGORY_SERVICE("Service", realm_app_error_category.RLM_APP_ERROR_CATEGORY_SERVICE.value.toInt()),
    RLM_APP_ERROR_CATEGORY_CUSTOM("Custom", realm_app_error_category.RLM_APP_ERROR_CATEGORY_CUSTOM.value.toInt());

    actual companion object {

        internal actual fun of(nativeValue: Int): AppErrorCategory? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
