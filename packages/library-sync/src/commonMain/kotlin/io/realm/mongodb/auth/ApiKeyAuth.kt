package io.realm.mongodb.auth

import io.realm.mongodb.App

interface ApiKeyAuth {
    /**
     * Returns the [io.realm.mongodb.User] that this instance in associated with.
     */
    val user: User

    /**
     * Returns the [io.realm.mongodb.App] that this instance in associated with.
     */
    val app: App

    /**
     * Creates a user API key that can be used to authenticate as the user.
     * The value of the key must be persisted at this time as this is the only time it is visible.
     * The key is enabled when created. It can be disabled by calling [.disable].
     *
     * @param name the name of the key
     * @throws AppException if the server failed to create the API key.
     * @return the new API key for the user.
     */
    suspend fun create(name: String): ApiKey

    /**
     * Fetches a specific user API key associated with the user.
     *
     * @param id the id of the key to fetch.
     * @throws AppException if the server failed to fetch the API key.
     * @return the API key associated with the given id.
     */
    suspend fun fetch(id: ObjectId): ApiKey

    /**
     * Fetches all API keys associated with the user.
     *
     * @throws AppException if the server failed to fetch the API keys.
     */
    suspend fun fetchAll(): List<ApiKey>

    /**
     * Deletes a specific API key created by the user.
     *
     * @param id the id of the key to delete.
     * @throws AppException if the server failed to delete the API key.
     */
    suspend fun delete(id: ObjectId)

    /**
     * Disables a specific API key created by the user.
     *
     * @param id the id of the key to disable.
     * @throws AppException if the server failed to disable the API key.
     */
    suspend fun disable(id: ObjectId)

    /**
     * Enables a specific API key created by the user.
     *
     * @param id the id of the key to enable.
     * @throws AppException if the server failed to enable the API key.
     */
    suspend fun enable(id: ObjectId)

}