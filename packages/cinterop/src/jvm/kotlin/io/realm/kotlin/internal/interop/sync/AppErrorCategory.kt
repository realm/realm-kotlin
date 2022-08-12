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

import io.realm.kotlin.internal.interop.NativeEnumerated
import io.realm.kotlin.internal.interop.realm_app_error_category_e

actual enum class AppErrorCategory(actual val description: String, override val nativeValue: Int) : NativeEnumerated {
    RLM_APP_ERROR_CATEGORY_HTTP("Http", realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_HTTP),
    RLM_APP_ERROR_CATEGORY_JSON("Json", realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_JSON),
    RLM_APP_ERROR_CATEGORY_CLIENT("Client", realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_CLIENT),
    RLM_APP_ERROR_CATEGORY_SERVICE("Service", realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_SERVICE),
    RLM_APP_ERROR_CATEGORY_CUSTOM("Custom", realm_app_error_category_e.RLM_APP_ERROR_CATEGORY_CUSTOM);

    actual companion object {

        internal actual fun of(nativeValue: Int): AppErrorCategory? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
