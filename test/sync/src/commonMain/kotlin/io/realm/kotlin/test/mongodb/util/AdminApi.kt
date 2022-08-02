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
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.test.mongodb.COMMAND_SERVER_BASE_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

private const val ADMIN_PATH = "/api/admin/v3.0"

/**
 * Wrapper around App Services Server Admin functions needed for tests.
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
    suspend fun triggerClientReset(
        userId: String,
        session: SyncSession,
        withRecoveryModeDisabled: Boolean = false,
        block: (suspend () -> Unit)? = null
    )

    /**
     * Changes the permissions for sync. Receives a lambda block which with your test logic.
     * It will safely revert to the original permissions even when an exception was thrown.
     */
    suspend fun changeSyncPermissions(permissions: SyncPermissions, block: suspend () -> Unit)

    /**
     * Modifies the server configuration for Sync.
     */
    suspend fun controlSync(
        enabled: Boolean,
        permissions: SyncPermissions? = null,
        recoveryModeDisabled: Boolean? = null
    )

    /**
     * Set whether or not automatic confirmation is enabled.
     */
    suspend fun setAutomaticConfirmation(enabled: Boolean)

    /**
     * Set whether or not custom confirmation is enabled.
     */
    suspend fun setCustomConfirmation(enabled: Boolean)

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
                .firstOrNull { it.jsonObject["client_app_id"]?.jsonPrimitive?.content == appName }?.jsonObject?.get(
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

    private suspend fun getBackingDBServiceId(): String {
        val typedRequest =
            client.typedRequest<JsonArray>(Get, "$url/groups/$groupId/apps/$appId/services")
        val result = typedRequest.filter {
            it.jsonObject["name"] == JsonPrimitive("BackingDB")
        }.let {
            it.first().jsonObject["_id"]!!.jsonPrimitive.content
        }
        val kajhsdkjh = 0
        return result
    }

    override suspend fun controlSync(
        enabled: Boolean,
        permissions: SyncPermissions?,
        recoveryModeDisabled: Boolean?
    ) {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            doControlSync(backingDbServiceId, enabled, permissions, recoveryModeDisabled)
        }
    }

    private suspend fun doControlSync(
        serviceId: String,
        enabled: Boolean,
        permissions: SyncPermissions? = null,
        recoveryModeDisabled: Boolean? = null
    ) {
        val url = "$url/groups/$groupId/apps/$appId/services/$serviceId/config"

        val originalConfig: JsonObject = getConfig(serviceId)

        val modifiedConfig = buildJsonObject {
            when {
                originalConfig["sync"] != null -> {
                    put(
                        key = "sync",
                        element = modifyPartitionSyncConfig(
                            originalConfig["sync"]!!.jsonObject,
                            enabled,
                            permissions,
                            recoveryModeDisabled
                        )
                    )
                }
                originalConfig["flexible_sync"] != null -> {
                    put(
                        key = "flexible_sync",
                        element = modifyFlexibleSyncConfig(
                            originalConfig["flexible_sync"]!!.jsonObject,
                            enabled,
                            recoveryModeDisabled
                        )
                    )
                }
                else -> throw IllegalStateException("Config for $serviceId should be either partition-based or flexible sync.")
            }
        }

        sendPatchRequest(url, modifiedConfig)
    }

    private fun modifyFlexibleSyncConfig(
        originalConfig: JsonObject,
        enabled: Boolean,
        recoveryModeDisabled: Boolean? = null
    ): JsonObject =
        JsonObject(
            originalConfig
                .toMutableMap()
                .apply {
                    this["enabled"] = JsonPrimitive(
                        if (enabled) "enabled" else "disabled"
                    )
                    if (recoveryModeDisabled != null) {
                        this["is_recovery_mode_disabled"] = JsonPrimitive(recoveryModeDisabled)
                    }
                }
        )

    private fun modifyPartitionSyncConfig(
        originalConfig: JsonObject,
        enabled: Boolean,
        permissions: SyncPermissions? = null,
        recoveryModeDisabled: Boolean? = null
    ): JsonObject =
        JsonObject(
            originalConfig
                .toMutableMap()
                .apply {
                    this["state"] = JsonPrimitive(
                        if (enabled) "enabled" else "disabled"
                    )
                    if (permissions != null) {
                        this["permissions"] = JsonObject(
                            mapOf<String, JsonElement>(
                                "read" to JsonPrimitive(permissions.read),
                                "write" to JsonPrimitive(permissions.read)
                            )
                        )
                    }
                    if (recoveryModeDisabled != null) {
                        this["is_recovery_mode_disabled"] = JsonPrimitive(recoveryModeDisabled)
                    }
                }
        )

    override suspend fun pauseSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            doControlSync(backingDbServiceId, false)
        }
    }

    override suspend fun startSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            doControlSync(backingDbServiceId, true)
        }
    }

    private suspend fun getConfig(serviceId: String): JsonObject {
        val url = "$url/groups/$groupId/apps/$appId/services/$serviceId/config"
        return client.typedRequest(Get, url)
    }

    private suspend fun isRecoveryModeDisabled(serviceId: String): Boolean {
        return getConfig(serviceId)
            .let { config ->
                when {
                    config["sync"] != null -> config["sync"]
                    config["flexible_sync"] != null -> config["flexible_sync"]
                    else -> throw IllegalStateException("Config for $serviceId should be either partition-based or flexible sync.")
                }
            }!!.jsonObject["is_recovery_mode_disabled"]
            ?.jsonPrimitive?.booleanOrNull ?: false

        // return getConfig(serviceId)["sync"]!!
        //     .jsonObject["is_recovery_mode_disabled"]
        //     ?.jsonPrimitive?.booleanOrNull ?: false
    }

    override suspend fun triggerClientReset(
        userId: String,
        session: SyncSession,
        withRecoveryModeDisabled: Boolean,
        block: (suspend () -> Unit)?
    ) {
        withContext(dispatcher) {
            val serviceId = getBackingDBServiceId()
            val isRecoveryModeDisabled = isRecoveryModeDisabled(serviceId)

            try {
                // All tests follow this pattern:
                // 1 - download changes
                session.downloadAllServerChanges()

                // 2 - pause session
                session.pause()

                // 3 - modify recovery mode setting for reset operation if needed
                doControlSync(serviceId, true, recoveryModeDisabled = withRecoveryModeDisabled)

                // 4 - possibly write something in the db and assert conditions
                block?.invoke()

                // 5 - trigger client reset
                // TODO test adding "__realm_sync_${appId}"
                deleteMDBDocumentByQuery("__realm_sync", "clientfiles", "{ownerId: \"$userId\"}")

                // 6 - resume session
                session.resume()
            } finally {
                // 7 - restore recovery mode setting
                doControlSync(serviceId, true, recoveryModeDisabled = isRecoveryModeDisabled)
            }
            Unit
        }
    }

    override suspend fun changeSyncPermissions(
        permissions: SyncPermissions,
        block: suspend () -> Unit
    ) {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()

            // Execute test logic
            try {
                doControlSync(backingDbServiceId, true, permissions)
                block.invoke()
            } finally {
                // Restore original permissions
                doControlSync(backingDbServiceId, true, SyncPermissions(read = true, write = true))
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

    override suspend fun setAutomaticConfirmation(enabled: Boolean) {
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/$appId/auth_providers/$providerId"
            val configData = JsonObject(mapOf("autoConfirm" to JsonPrimitive(enabled)))
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
        }
    }

    override suspend fun setCustomConfirmation(enabled: Boolean) {
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/$appId/auth_providers/$providerId"
            val configData = mapOf(
                "runConfirmationFunction" to JsonPrimitive(enabled)
            ).let {
                JsonObject(it)
            }
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
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

    // Work-around for https://github.com/realm/realm-kotlin/issues/519 where PATCH
    // messages are being sent through our own node command server instead of using Ktor.
    private suspend fun sendPatchRequest(url: String, requestBody: JsonObject) {
        val forwardUrl = "$COMMAND_SERVER_BASE_URL/forward-as-patch"

        // It is unclear exactly what is happening, but if we only send the request once
        // it appears as the server accepts it, but is delayed deploying the changes,
        // i.e. the change will appear correct in the UI, but later requests against
        // the server will fail in a way that suggest the change wasn't applied after all.
        // Sending these requests twice seems to fix most race conditions.
        repeat(2) {
            client.request<HttpResponse>(forwardUrl) {
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

        // For the last remaining race conditions (on JVM), delaying a bit seems to do the trick
        delay(1000)
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
