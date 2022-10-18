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

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.types.ObjectId

/**
 * Exposes functionality for a user to manage API keys under their control.
 */
public interface ApiKeyAuth {

    /**
     * The User that this instance in associated with.
     */
    public val user: User

    /**
     * The App that this instance in associated with.
     */
    public val app: App

    /**
     * Creates a user API key that can be used to authenticate as the user.
     * The value of the key must be persisted at this time as this is the only time it is visible.
     * The key is enabled when created. It can be disabled by calling the disable method.
     *
     * @param name the name of the key
     * @throws IllegalArgumentException if an invalid name for the key is sent to the server.
     * @return the new API key for the user.
     */
    public suspend fun create(name: String): ApiKey

    /**
     * Deletes a specific API key created by the user.
     * Returns silently if no key is deleted.
     * @param id the id of the key to delete.
     */
    public suspend fun delete(id: ObjectId)

    /**
     * Disables a specific API key created by the user.
     *
     * @param id the id of the key to disable.
     * @throws IllegalArgumentException if a non existing API key is disabled.
     */
    public suspend fun disable(id: ObjectId)

    /**
     * Enables a specific API key created by the user.
     *
     * @param id the id of the key to disable.
     * @throws IllegalArgumentException if a non existing API key is enabled.
     */
    public suspend fun enable(id: ObjectId)

    /**
     * Fetches a specific user API key associated with the user.
     *
     * @param id the id of the key to fetch.
     * @throws IllegalArgumentException if a non existing API key is fetched.
     */
    public suspend fun fetch(id: ObjectId): ApiKey?

    /**
     * Fetches all API keys associated with the user.
     * Returns an empty list if no key is found.
     */
    public suspend fun fetchAll(): List<ApiKey>
}
