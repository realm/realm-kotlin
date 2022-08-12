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

import io.realm.kotlin.internal.interop.realm_app_errno_json_e

actual enum class JsonErrorCode(
    override val description: String,
    override val nativeValue: Int
) : ErrorCodeDescription {
    RLM_APP_ERR_JSON_BAD_TOKEN("BadToken", realm_app_errno_json_e.RLM_APP_ERR_JSON_BAD_TOKEN),
    RLM_APP_ERR_JSON_MALFORMED_JSON("MalformedJson", realm_app_errno_json_e.RLM_APP_ERR_JSON_MALFORMED_JSON),
    RLM_APP_ERR_JSON_MISSING_JSON_KEY("MissingJsonKey", realm_app_errno_json_e.RLM_APP_ERR_JSON_MISSING_JSON_KEY),
    RLM_APP_ERR_JSON_BAD_BSON_PARSE("BadBsonParse", realm_app_errno_json_e.RLM_APP_ERR_JSON_BAD_BSON_PARSE);

    actual companion object {
        internal actual fun of(nativeValue: Int): JsonErrorCode? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
