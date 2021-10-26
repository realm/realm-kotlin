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

import io.realm.internal.interop.CinteropEnumCompanion

expect enum class ErrorCategory {
    /**
     * Error category for HTTP-related errors. The error code value can be interpreted as a HTTP status code.
     */
    RLM_APP_ERROR_CATEGORY_HTTP,
    /**
     * JSON response parsing related errors. The error code is a member of realm_app_errno_json_e.
     */
    RLM_APP_ERROR_CATEGORY_JSON,
    /**
     * Client-side related errors. The error code is a member of realm_app_errno_client_e.
     */
    RLM_APP_ERROR_CATEGORY_CLIENT,
    /**
     * Errors reported by the backend. The error code is a member of realm_app_errno_service_e.
     */
    RLM_APP_ERROR_CATEGORY_SERVICE,
    /**
     * Custom error code was set in realm_http_response_t.custom_status_code.
     * The error code is the custom_status_code value.
     */
    RLM_APP_ERROR_CATEGORY_CUSTOM;

    companion object: CinteropEnumCompanion<ErrorCategory>
}
