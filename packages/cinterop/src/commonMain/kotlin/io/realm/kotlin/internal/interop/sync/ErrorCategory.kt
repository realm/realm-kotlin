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
 * Wrapper for C-API `realm_app_error_category`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2522
 */
expect enum class ErrorCategory : CodeDescription {
    RLM_ERR_CAT_LOGIC, // illegalstate
    RLM_ERR_CAT_RUNTIME, // illegalstate / runtime
    RLM_ERR_CAT_INVALID_ARG, // invalidargument
    RLM_ERR_CAT_FILE_ACCESS, // runtime
    RLM_ERR_CAT_SYSTEM_ERROR, // runtime
    RLM_ERR_CAT_APP_ERROR, // runtime
    RLM_ERR_CAT_CLIENT_ERROR, // app error
    RLM_ERR_CAT_JSON_ERROR, // app error
    RLM_ERR_CAT_SERVICE_ERROR, // app error
    RLM_ERR_CAT_HTTP_ERROR, // app error
    RLM_ERR_CAT_CUSTOM_ERROR; // app error

    companion object {
        internal fun of(nativeValue: Int): ErrorCategory?
    }
}
