/*
 * Copyright 2020 Realm Inc.
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
package io.realm.mongodb.auth

import org.bson.types.ObjectId
import io.realm.mongodb.User

/**
 * Class representing an API key for a [User]. An API can be used to represent the
 * user when logging instead of using email and password.
 *
 * These keys are created or fetched through [ApiKeyAuth.create] or the various
 * `fetch`-methods.
 *
 * Note that a keys [.value] is only available when the key is created, after that it is not
 * visible. So anyone creating an API key is responsible for storing it safely after that.
 */
data class ApiKey(
    /**
     * The unique identifier for this key.
     */
    val id: ObjectId,

    /**
     * The actual key value. This value is only returned when the key is created. After that
     * the value is no longer visible.
     */
    val value: String?,

    /**
     * The human-readable name for the key.
     */
    val name: String,

    /**
     * Whether or not this key is currently enabled.
     */
    val isEnabled: Boolean
)
