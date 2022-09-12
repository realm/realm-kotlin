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

package io.realm.kotlin.test.mongodb.util

import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.mongodb.COMMAND_SERVER_BASE_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

private const val ADMIN_PATH = "/api/admin/v3.0"

/**
 * Types of user confirmation supported by the EmailAuth authentication provider.
 */
enum class UserConfirmationMode {
    AUTOMATIC_CONFIRMATION,
    CUSTOM_CONFIRMATION,
    SEND_EMAIL
}

/**
 * Wrapper around App Services Server Admin functions needed for tests.
 *
 * WARNING: Modifying config options for and app using the Admin API does not always seem to work.
 * The root cause is unclear, but could be a race condition on the server. For that reason, it is
 * probably better to use different test apps, rather than modifying an existing app.
 */
interface AdminApi {

    public val dispatcher: CoroutineDispatcher

    /**
     * Deletes all currently registered and pending users on the App Services Application .
     *
     * Warning: This will run using `runBlocking`.
     */
    suspend fun deleteAllUsers()

    /**
     * Deactivates Sync on the server. This will not cause existing sessions to fail,
     * they will instead attempt to reconnect later.
     */
    suspend fun pauseSync()

    /**
     * Activates Sync on the server.
     */
    suspend fun startSync()

    /**
     * Trigger a client reset by deleting user-related files in the server.
     */
    suspend fun triggerClientReset(userId: String)

    /**
     * Changes the permissions for sync. Receives a lambda block which with your test logic.
     * It will safely revert to the original permissions even when an exception was thrown.
     */
    suspend fun changeSyncPermissions(permissions: SyncPermissions, block: () -> Unit)

    /**
     * Set type of authentication method used for the EmailAuth provider.
     */
    suspend fun setAuth(auth: UserConfirmationMode)

    /**
     * Set whether or not using a reset function is available.
     */
    suspend fun setResetFunction(enabled: Boolean)

    /**
     * Return the JSON configuration for the Email/Password auth provider.
     */
    suspend fun getAuthConfigData(): String

    /**
     * Insert a MongoDB document which will be eventually synced as RealmObject.
     */
    suspend fun insertDocument(clazz: String, json: String): JsonObject

    /**
     * Query the specified database and collection
     */
    suspend fun queryDocumentById(clazz: String, oid: String): JsonObject

    fun closeClient()
}

data class SyncPermissions(
    val read: Boolean,
    val write: Boolean
)

open class AdminApiImpl internal constructor(
    baseUrl: String,
    private val appName: String,
    private val debug: Boolean,
    override val dispatcher: CoroutineDispatcher
) : AdminApi {
    private val url = baseUrl + ADMIN_PATH
    private val MDB_DATABASE_NAME: String =
        "test_data" // as defined in realm-kotlin/tools/sync_test_server/app_template/config.json
    private lateinit var client: HttpClient
    private lateinit var groupId: String
    private lateinit var appId: String

    // Convenience serialization classes for easier access to server responses
    @Serializable
    data class LoginResponse(val access_token: String)

    @Serializable
    data class Role(val group_id: String)

    @Serializable
    data class Profile(val roles: List<Role>)

    @Serializable
    data class ServerApp(val client_app_id: String, val _id: String)

    init {
        // Work around issues on Native with the Ktor client being created and used
        // on different threads.
        runBlocking(Dispatchers.Unconfined) {
            // Log in using unauthorized client
            val unauthorizedClient = defaultClient("$appName-unauthorized", debug)
            val loginResponse = unauthorizedClient.typedRequest<LoginResponse>(
                HttpMethod.Post,
                "$url/auth/providers/local-userpass/login"
            ) {
                contentType(ContentType.Application.Json)
                body = mapOf("username" to "unique_user@domain.com", "password" to "password")
            }

            // Setup authorized client for the rest of the requests
            // Client is currently being constructured for each network reques to work around
            // https://github.com/realm/realm-kotlin/issues/480
            val accessToken = loginResponse.access_token
            unauthorizedClient.close()

            client = defaultClient("$appName-authorized", debug) {
                defaultRequest {
                    headers {
                        append("Authorization", "Bearer $accessToken")
                    }
                }
                install(JsonFeature) {
                    serializer = KotlinxSerializer()
                }
                install(Logging) {
                    // Set to LogLevel.ALL to debug Admin API requests. All relevant
                    // data for each request/response will be console or LogCat.
                    level = LogLevel.INFO
                }
            }

            // Collect app group id
            groupId = client.typedRequest<Profile>(Get, "$url/auth/profile")
                .roles.first().group_id

            // Get app id
            appId = client.typedRequest<JsonArray>(Get, "$url/groups/$groupId/apps")
                .firstOrNull { it.jsonObject["client_app_id"]?.jsonPrimitive?.content!!.startsWith(appName) }?.jsonObject?.get(
                    "_id"
                )?.jsonPrimitive?.content
                ?: error("App $appName not found")
        }
    }

    /**
     * Deletes all currently registered and pending users on the App Services Application.
     */
    override suspend fun deleteAllUsers() {
        withContext(dispatcher) {
            deleteAllRegisteredUsers()
            deleteAllPendingUsers()
        }
    }

    private suspend fun deleteAllPendingUsers() {
        val pendingUsers =
            client.typedRequest<JsonArray>(
                Get,
                "$url/groups/$groupId/apps/$appId/user_registrations/pending_users"
            )
        for (pendingUser in pendingUsers) {
            val loginTypes = pendingUser.jsonObject["login_ids"]!!.jsonArray
            loginTypes
                .filter { it.jsonObject["id_type"]!!.jsonPrimitive.content == "email" }
                .map {
                    client.delete<Unit>(
                        "$url/groups/$groupId/apps/$appId/user_registrations/by_email/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    )
                }
        }
    }

    private suspend fun deleteAllRegisteredUsers() {
        val users = client.typedRequest<JsonArray>(
            Get,
            "$url/groups/$groupId/apps/$appId/users"
        )
        users.map {
            client.delete<Unit>("$url/groups/$groupId/apps/$appId/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
        }
    }

    private suspend fun getBackingDBServiceId(): String =
        client.typedRequest<JsonArray>(Get, "$url/groups/$groupId/apps/$appId/services")
            .filter {
                it.jsonObject["name"] == JsonPrimitive("BackingDB")
            }
            .let {
                it.first().jsonObject["_id"]!!.jsonPrimitive.content
            }

    private suspend fun controlSync(
        serviceId: String,
        enabled: Boolean,
        permissions: SyncPermissions? = null
    ) {
        val url = "$url/groups/$groupId/apps/$appId/services/$serviceId/config"
        val syncEnabled = if (enabled) "enabled" else "disabled"
        val jsonPartition = permissions?.let {
            val permissionList = JsonObject(
                mapOf(
                    "read" to JsonPrimitive(permissions.read),
                    "write" to JsonPrimitive(permissions.read)
                )
            )
            JsonObject(mapOf("permissions" to permissionList, "key" to JsonPrimitive("realm_id")))
        }

        // Add permissions if present, otherwise just change state
        val content = jsonPartition?.let {
            mapOf(
                "state" to JsonPrimitive(syncEnabled),
                "partition" to jsonPartition
            )
        } ?: mapOf("state" to JsonPrimitive(syncEnabled))

        val configObj = JsonObject(mapOf("sync" to JsonObject(content)))
        sendPatchRequest(url, configObj)
    }

    override suspend fun pauseSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            controlSync(backingDbServiceId, false)
        }
    }

    override suspend fun startSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            controlSync(backingDbServiceId, true)
        }
    }

    override suspend fun triggerClientReset(userId: String) {
        withContext(dispatcher) {
            deleteMDBDocumentByQuery("__realm_sync", "clientfiles", "{ownerId:\"$userId\"}")
        }
    }

    override suspend fun changeSyncPermissions(permissions: SyncPermissions, block: () -> Unit) {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()

            // Execute test logic
            try {
                controlSync(backingDbServiceId, true, permissions)
                block.invoke()
            } finally {
                // Restore original permissions
                controlSync(backingDbServiceId, true, SyncPermissions(read = true, write = true))
            }
        }
    }

    override suspend fun getAuthConfigData(): String {
        return withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/$appId/auth_providers/$providerId"
            client.typedRequest<JsonObject>(Get, url).toString()
        }
    }

    override fun closeClient() {
        client.close()
    }

    override suspend fun setAuth(auth: UserConfirmationMode) {
        val result: Pair<Boolean, Boolean> = when (auth) {
            UserConfirmationMode.AUTOMATIC_CONFIRMATION -> Pair(true, false)
            UserConfirmationMode.CUSTOM_CONFIRMATION -> Pair(false, true)
            UserConfirmationMode.SEND_EMAIL -> Pair(false, false)
        }
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/$appId/auth_providers/$providerId"
            val configData = JsonObject(
                mapOf(
                    "autoConfirm" to JsonPrimitive(result.first),
                    "runConfirmationFunction" to JsonPrimitive(result.second)
                )
            )
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
            client.typedRequest<JsonObject>(Get, url).let {
                if (it["config"]!!.jsonObject["autoConfirm"]!!.jsonPrimitive.boolean != result.first) {
                    throw IllegalStateException("Expected: $result, was $it")
                }
                if (it["config"]!!.jsonObject["runConfirmationFunction"]!!.jsonPrimitive.boolean != result.second) {
                    throw IllegalStateException("Expected: $result, was $it")
                }
            }
        }
    }

    override suspend fun setResetFunction(enabled: Boolean) {
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/$appId/auth_providers/$providerId"
            val configData = mapOf(
                "runResetFunction" to JsonPrimitive(enabled)
            ).let {
                JsonObject(it)
            }
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
        }
    }

    override suspend fun insertDocument(clazz: String, json: String): JsonObject {
        return withContext(dispatcher) {
            insertMDBDocument(MDB_DATABASE_NAME, clazz, json)
        }
    }

    override suspend fun queryDocumentById(clazz: String, oid: String): JsonObject {
        return withContext(dispatcher) {
            queryMDBDocumentById(MDB_DATABASE_NAME, clazz, oid)
        }
    }

    private suspend fun getLocalUserPassProviderId(): String {
        return withContext(dispatcher) {
            client.typedRequest<JsonArray>(Get, "$url/groups/$groupId/apps/$appId/auth_providers")
                .let { arr: JsonArray ->
                    arr.firstOrNull { el: JsonElement ->
                        el.jsonObject["name"]!!.jsonPrimitive.content == "local-userpass"
                    }?.let { el: JsonElement ->
                        el.jsonObject["_id"]?.jsonPrimitive?.content ?: throw IllegalStateException(
                            "Could not find '_id': $arr"
                        )
                    } ?: throw IllegalStateException("Could not find local-userpass provider: $arr")
                }
        }
    }

    // Wrap PATCH requests due to previous problems with it. Keep this wrapper until we are
    // confident all problems have been resolved: https://github.com/realm/realm-kotlin/issues/519.
    private suspend fun sendPatchRequest(url: String, requestBody: JsonObject) {
        client.request<HttpResponse>(url) {
            method = Post
            parameter("url", url)
            contentType(ContentType.Application.Json)
            body = requestBody
        }.let {
            if (!it.status.isSuccess()) {
                throw IllegalStateException("PATCH request failed: $it")
            }
        }
    }

    private suspend fun insertMDBDocument(
        dbName: String,
        collection: String,
        jsonPayload: String
    ): JsonObject {
        val url = "$COMMAND_SERVER_BASE_URL/insert-document?db=$dbName&collection=$collection"

        return client.typedRequest<JsonObject>(Put, url) {
            body = Json.decodeFromString<JsonObject>(jsonPayload)
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun deleteMDBDocumentByQuery(
        dbName: String,
        collection: String,
        query: String
    ): JsonObject {
        val url =
            "$COMMAND_SERVER_BASE_URL/delete-document?db=$dbName&collection=$collection&query=$query"

        return client.typedRequest<JsonObject>(Get, url)
    }

    private suspend fun queryMDBDocumentById(
        dbName: String,
        collection: String,
        oid: String
    ): JsonObject {
        val url =
            "$COMMAND_SERVER_BASE_URL/query-document-by-id?db=$dbName&collection=$collection&oid=$oid"

        return client.typedRequest<JsonObject>(Get, url)
    }

    // Default serializer fails with
    // InvalidMutabilityException: mutation attempt of frozen kotlin.collections.HashMap
    // on native. Have tried the various workarounds from
    // https://github.com/Kotlin/kotlinx.serialization/issues/1450
    // but only one that works is manual invoking the deserializer
    @OptIn(InternalSerializationApi::class)
    private suspend inline fun <reified T : Any> HttpClient.typedRequest(
        method: HttpMethod,
        url: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return this@typedRequest.request<HttpResponse>(url) {
            this.method = method
            this.apply(block)
        }.readText()
            .let {
                Json { ignoreUnknownKeys = true }.decodeFromString(
                    T::class.serializer(),
                    it
                )
            }
    }
}
