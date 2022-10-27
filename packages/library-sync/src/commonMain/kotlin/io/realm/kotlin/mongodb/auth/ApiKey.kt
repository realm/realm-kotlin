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
package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.types.ObjectId
import org.mongodb.kbson.BsonObjectId

/**
 * Class representing an API key for a [User]. An API key can be used to represent a
 * user when logging in instead of using email and password.
 * Note that the value of a key will only be available immediately after the key is created, after
 * which point it is not visible anymore. This means that keys returned by [ApiKeyAuth.fetch] and
 * [ApiKeyAuth.fetchAll] will have a `null` [value]. Anyone creating an API key is responsible for
 * storing it safely after that.
 *
 * @param id an [ObjectId] uniquely identifying the key.
 * @param value the value of this key, only returned when the key is created, `null` otherwise.
 * @param name the name of the key.
 * @param enabled whether the key is enabled or not.
 */
public data class ApiKey internal constructor(
    public val id: BsonObjectId,
    public val value: String?,
    public val name: String,
    public val enabled: Boolean
) {
    internal constructor(
        id: ObjectId,
        value: String?,
        name: String,
        enabled: Boolean
    ) : this(id.asBsonObjectId(), value, name, enabled)
}
