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
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.contentType
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.mongodb.TEST_APP_1
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_SERVER_BASE_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

private const val ADMIN_PATH = "/api/admin/v3.0"

/**
 * Wrapper around App Services Server Admin functions needed for tests.
 */
interface AdminApi {

    public val dispatcher: CoroutineDispatcher

    fun getClientAppId(): String

    suspend fun deleteApp()

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
    suspend fun insertDocument(clazz: String, json: String): JsonObject?

    /**
     * Query the specified database and collection
     */
    suspend fun queryDocument(clazz: String, query: String): JsonObject?

    fun closeClient()

    interface Builder {
        val functions: Map<String, Pair<String, String>>

        suspend fun addAuthProvider(vararg authProviders: AppAuthProvider)

        suspend fun addFunction(vararg functions: AppFunction)

        // suspend fun addSchema(vararg schemas: AppSchema) = TODO()

        suspend fun setPartition(
            enabled: Boolean = true,
            key: String = "realm_id",
            type: String = "string",
            required: Boolean = false,
            permissions: String = """
                {
                    "read": true,
                    "write": true
                 }
            """.trimIndent()
        )

        suspend fun setFlexible(
            db: String,
            enabled: Boolean,
            queryableFieldsName: List<String>,
            permissions: String = """
                {
                    "rules": {},
                    "defaultRoles": [{
                        "name": "all",
                        "applyWhen": {},
                        "read": true,
                        "write": true
                    }]
                }
            """.trimIndent()
        )
    }
}

data class SyncPermissions(
    val read: Boolean,
    val write: Boolean
)

@OptIn(InternalSerializationApi::class)
open class AdminApiImpl constructor(
    baseUrl: String,
    private val appName: String,
    private val debug: Boolean,
    override val dispatcher: CoroutineDispatcher,
    val customBuilder: suspend AdminApi.Builder.() -> Unit
) : AdminApi {
    private val url = baseUrl + ADMIN_PATH

    private lateinit var client: HttpClient
    private lateinit var groupId: String

    private lateinit var serverApp: ServerApp
    private lateinit var dbService: Service
    private lateinit var dbName: String

    // Convenience serialization classes for easier access to server responses
    @Serializable
    data class LoginResponse(val access_token: String)

    @Serializable
    data class Role(val group_id: String)

    @Serializable
    data class Profile(val roles: List<Role>)

    @Serializable
    data class ServerApp(
        val _id: String,
        val client_app_id: String,
        val name: String,
        val domain_id: String,
        val group_id: String
    )

    @Serializable
    data class Service(val _id: String, val name: String, val type: String)

    @Serializable
    data class Function(val _id: String, val name: String)

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

            createApp()
        }
    }


    class Builder(val adminApi: AdminApiImpl): AdminApi.Builder {
        override suspend fun addAuthProvider(vararg authProviders: AppAuthProvider) {
            authProviders.forEach { authProvider ->
                adminApi.addAuthProvider(authProvider)
            }
        }

        override suspend fun addFunction(vararg functions: AppFunction) {
            functions.forEach { function ->
                adminApi.addFunction(function).let {
                    functionsMutable[it.name] = it._id to it.name
                }
            }
        }

        override suspend fun setPartition(
            enabled: Boolean,
            key: String,
            type: String,
            required: Boolean,
            permissions: String
        ) {
            adminApi.setSyncConfig(
                """
                {
                    "sync": {
                        "state": ${if(enabled) "enabled" else "disabled"},
                        "database_name": "${adminApi.dbName}",
                        "partition": {
                            "key": "$key",
                            "type": "$type",
                            "required": $required,
                            "permissions": $permissions
                        }
                    }
                }
            """.trimIndent()
            )
        }

        override suspend fun setFlexible(
            db: String,
            enabled: Boolean,
            queryableFieldsName: List<String>,
            permissions: String
        ) {
            adminApi.setSyncConfig(
                """
                    "flexible_sync": {
                        "state": ${if(enabled) "enabled" else "disabled"},
                        "database_name": "${adminApi.dbName}",
                        "queryable_fields_names": ${Json.encodeToString(queryableFieldsName)},
                        "permissions": $permissions
                    }
            """.trimIndent()
            )
        }

        private var functionsMutable: MutableMap<String, Pair<String, String>> = mutableMapOf()

        override val functions: Map<String, Pair<String, String>>
            get() = functionsMutable
    }

    private suspend fun createApp() {
        serverApp = createNewApp()
        dbName = serverApp.client_app_id

        addFunction(AppConfigs.forwardAsPatch)

        dbService = addService(
            """
                {
                    "name": "BackingDB",
                    "type": "mongodb",
                    "config": { "uri": "mongodb://localhost:26000" }
                }
            """.trimIndent()
        )

        customBuilder(Builder(this))

        // FIXME add schema

        // when (appName) {
        //     TEST_APP_1 -> setSyncConfig(
        //         """
        //         {
        //         "sync": {
        //             "state": "enabled",
        //             "database_name": "$dbName",
        //             "partition": {
        //                 "key": "realm_id",
        //                 "type": "string",
        //                 "required": false,
        //                 "permissions": {
        //                     "read": true,
        //                     "write": true
        //                 }
        //             }
        //         }
        //     }
        //     """.trimIndent()
        //     )
        //     TEST_APP_FLEX -> setSyncConfig(
        //         """
        //     {
        //         "flexible_sync": {
        //             "state": "enabled",
        //             "database_name": "$dbName",
        //             "queryable_fields_names": ["name", "section"],
        //             "permissions": {
        //                 "rules": {},
        //                 "defaultRoles": [{
        //                     "name": "all",
        //                     "applyWhen": {},
        //                     "read": true,
        //                     "write": true
        //                 }]
        //             }
        //         }
        //     }
        //     """.trimIndent()
        //     )
        // }

        setDevelopmentMode(true)

        // addRule(
        //     """
        //         {
        //             "database": "$dbName",
        //             "collection": "UserData",
        //             "roles": [{
        //                 "name": "default",
        //                 "apply_when": {},
        //                 "insert": true,
        //                 "delete": true,
        //                 "additional_fields": {}
        //             }]
        //         }
        //     """.trimIndent()
        // )
        //
        // setCustomUserData(
        //     """
        //         {
        //             "mongo_service_id": ${dbService._id},
        //             "enabled": true,
        //             "database_name": "$dbName",
        //             "collection_name": "UserData",
        //             "user_id_field": "user_id"
        //         }
        //     """.trimIndent()
        // )

        // addSecret(
        //     """
        //         {
        //             "name": "gcm",
        //             "value": "gcm"
        //         }
        //     """.trimIndent()
        // )
        //
        // addService(
        //     """
        //         {
        //             "name": "gcm",
        //             "type": "gcm",
        //             "config": {
        //                 "senderId": "gcm"
        //             },
        //             "secret_config": {
        //                 "apiKey": "gcm"
        //             },
        //             "version": 1
        //         }
        //     """.trimIndent()
        // )
    }

    private suspend fun createNewApp(): ServerApp = withContext(dispatcher) {
        client.typedRequest<ServerApp>(Post, "$url/groups/$groupId/apps") {
            body = Json.parseToJsonElement("""{"name": $appName}""")
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun addAuthProvider(authProvider: AppAuthProvider): JsonObject =
        withContext(dispatcher) {
            client.typedRequest<JsonObject>(
                Post,
                "$url/groups/$groupId/apps/${serverApp._id}/auth_providers"
            ) {
                body = authProvider
                contentType(ContentType.Application.Json)
            }
        }

    private suspend fun addSecret(secret: String): JsonObject = withContext(dispatcher) {
        client.typedRequest<JsonObject>(
            Post,
            "$url/groups/$groupId/apps/${serverApp._id}/secrets"
        ) {
            body = Json.parseToJsonElement(secret)
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun addService(service: String): Service = withContext(dispatcher) {
        client.typedRequest<Service>(Post, "$url/groups/$groupId/apps/${serverApp._id}/services") {
            body = Json.parseToJsonElement(service)
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun addFunction(function: AppFunction): Function =
        withContext(dispatcher) {
            client.typedRequest<Function>(
                Post,
                "$url/groups/$groupId/apps/${serverApp._id}/functions"
            ) {
                body = function
                contentType(ContentType.Application.Json)
            }
        }

    private suspend fun setSyncConfig(config: String) = sendPatchRequest(
        url = "$url/groups/$groupId/apps/${serverApp._id}/services/${dbService._id}/config",
        requestBody = Json.parseToJsonElement(config).jsonObject
    )

    private suspend fun setDevelopmentMode(developmentModeEnabled: Boolean) =
        withContext(dispatcher) {
            client.request<HttpResponse>("$url/groups/$groupId/apps/${serverApp._id}/sync/config") {
                this.method = HttpMethod.Put
                body =
                    Json.parseToJsonElement("""{"development_mode_enabled": $developmentModeEnabled}""")
                contentType(ContentType.Application.Json)
            }
        }

    private suspend fun addRule(rule: String): JsonObject = withContext(dispatcher) {
        client.typedRequest<JsonObject>(
            Post,
            "$url/groups/$groupId/apps/${serverApp._id}/services/${dbService._id}/rules"
        ) {
            body = Json.parseToJsonElement(rule)
            contentType(ContentType.Application.Json)
        }
    }

    private suspend fun setCustomUserData(userDataConfig: String) = sendPatchRequest(
        url = "$url/groups/$groupId/apps/${serverApp._id}/custom_user_data",
        requestBody = Json.parseToJsonElement(userDataConfig).jsonObject
    )

    override fun getClientAppId(): String = serverApp.client_app_id

    override suspend fun deleteApp() {
        withContext(dispatcher) {
            client.request<HttpResponse>("$url/groups/$groupId/apps/${serverApp._id}") {
                this.method = HttpMethod.Delete
            }
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
                "$url/groups/$groupId/apps/${serverApp._id}/user_registrations/pending_users"
            )
        for (pendingUser in pendingUsers) {
            val loginTypes = pendingUser.jsonObject["login_ids"]!!.jsonArray
            loginTypes
                .filter { it.jsonObject["id_type"]!!.jsonPrimitive.content == "email" }
                .map {
                    client.delete<Unit>(
                        "$url/groups/$groupId/apps/${serverApp._id}/user_registrations/by_email/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    )
                }
        }
    }

    private suspend fun deleteAllRegisteredUsers() {
        val users = client.typedRequest<JsonArray>(
            Get,
            "$url/groups/$groupId/apps/${serverApp._id}/users"
        )
        users.map {
            client.delete<Unit>("$url/groups/$groupId/apps/${serverApp._id}/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
        }
    }

    private suspend fun getBackingDBService(): Service =
        client.typedListRequest<Service>(Get, "$url/groups/$groupId/apps/${serverApp._id}/services")
            .first {
                it.type == "mongodb"
            }

    private suspend fun controlSync(
        serviceId: String,
        enabled: Boolean,
        permissions: SyncPermissions? = null
    ) {
        val url = "$url/groups/$groupId/apps/${serverApp._id}/services/$serviceId/config"
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
            val backingDbServiceId = getBackingDBService()._id
            controlSync(backingDbServiceId, false)
        }
    }

    override suspend fun startSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBService()._id
            controlSync(backingDbServiceId, true)
        }
    }

    override suspend fun triggerClientReset(userId: String) {
        withContext(dispatcher) {
            deleteDocument("__realm_sync", "clientfiles", """{"ownerId": "$userId"}""")
        }
    }

    override suspend fun changeSyncPermissions(permissions: SyncPermissions, block: () -> Unit) {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBService()._id

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
            val url = "$url/groups/$groupId/apps/${serverApp._id}/auth_providers/$providerId"
            client.typedRequest<JsonObject>(Get, url).toString()
        }
    }

    override fun closeClient() {
        client.close()
    }

    override suspend fun setAutomaticConfirmation(enabled: Boolean) {
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/${serverApp._id}/auth_providers/$providerId"
            val configData = JsonObject(mapOf("autoConfirm" to JsonPrimitive(enabled)))
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
        }
    }

    override suspend fun setCustomConfirmation(enabled: Boolean) {
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/groups/$groupId/apps/${serverApp._id}/auth_providers/$providerId"
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
            val url = "$url/groups/$groupId/apps/${serverApp._id}/auth_providers/$providerId"
            val configData = mapOf(
                "runResetFunction" to JsonPrimitive(enabled)
            ).let {
                JsonObject(it)
            }
            val configObj = JsonObject(mapOf("config" to configData))
            sendPatchRequest(url, configObj)
        }
    }

    override suspend fun insertDocument(clazz: String, json: String): JsonObject? {
        return withContext(dispatcher) {
            functionCall(
                name = "insertDocument",
                arguments = buildJsonArray {
                    add(getBackingDBService().name)
                    add(dbName)
                    add(clazz)
                    add(Json.decodeFromString<JsonObject>(json))
                }
            )
        }
    }

    override suspend fun queryDocument(clazz: String, query: String): JsonObject? {
        return withContext(dispatcher) {
            functionCall(
                name = "queryDocument",
                arguments = buildJsonArray {
                    add(getBackingDBService().name)
                    add(dbName)
                    add(clazz)
                    add(query)
                }
            )
        }
    }

    private suspend fun deleteDocument(db: String, clazz: String, query: String): JsonObject? {
        return withContext(dispatcher) {
            functionCall(
                name = "deleteDocument",
                arguments = buildJsonArray {
                    add(getBackingDBService().name)
                    add(db)
                    add(clazz)
                    add(query)
                }
            )
        }
    }

    private suspend fun getLocalUserPassProviderId(): String {
        return withContext(dispatcher) {
            client.typedRequest<JsonArray>(
                Get,
                "$url/groups/$groupId/apps/${serverApp._id}/auth_providers"
            )
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
        functionCall(
            name = "forwardAsPatch",
            arguments = buildJsonArray {
                add(url)
                add(requestBody)
            }
        ).let {
            val statusCode = it!!["statusCode"]!!
                .jsonObject["${'$'}numberInt"]!!
                .jsonPrimitive.content.toInt()

            if (statusCode > 300) {
                throw IllegalStateException("Forward patch request failed $this")
            }
        }

        // For the last remaining race conditions (on JVM), delaying a bit seems to do the trick
        delay(1000)
    }

    private suspend fun functionCall(
        name: String,
        arguments: JsonArray
    ): JsonObject? {
        val functionCall = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }

        return withContext(dispatcher) {
            val url =
                "$url/groups/$groupId/apps/${serverApp._id}/debug/execute_function?run_as_system=true"
            client.typedRequest<JsonObject>(Post, url) {
                body = functionCall
                contentType(ContentType.Application.Json)
            }.jsonObject["result"]!!.let {
                when (it) {
                    is JsonNull -> null
                    else -> it.jsonObject
                }
            }
        }
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

    // Default serializer fails with
    // InvalidMutabilityException: mutation attempt of frozen kotlin.collections.HashMap
    // on native. Have tried the various workarounds from
    // https://github.com/Kotlin/kotlinx.serialization/issues/1450
    // but only one that works is manual invoking the deserializer
    @OptIn(InternalSerializationApi::class)
    private suspend inline fun <reified T : Any> HttpClient.typedListRequest(
        method: HttpMethod,
        url: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): List<T> {
        return this@typedListRequest.request<HttpResponse>(url) {
            this.method = method
            this.apply(block)
        }.readText()
            .let {
                Json { ignoreUnknownKeys = true }.decodeFromString(
                    ListSerializer(T::class.serializer()),
                    it
                )
            }
    }
}
