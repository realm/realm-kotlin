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

package io.realm.kotlin.test.mongodb.util

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initialize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

private const val ADMIN_PATH = "/api/admin/v3.0"

data class SyncPermissions(
    val read: Boolean,
    val write: Boolean
)

@Serializable
data class LoginResponse(val access_token: String)

@Serializable
data class Profile(val roles: List<Role>)

@Serializable
data class Role(val group_id: String)

@Serializable
data class AuthProvider constructor(
    val _id: String,
    val type: String,
    val disabled: Boolean = false,
    @Transient val app: BaasApp? = null
)

@Serializable
data class Service(
    val _id: String,
    val name: String,
    val type: String,
    @Transient val app: BaasApp? = null
)

@Serializable
data class Function(
    val _id: String? = null,
    val name: String,
    val source: String? = null,
    @SerialName("run_as_system") val runAsSystem: Boolean? = null,
    val private: Boolean? = null
)

@Serializable
data class BaasApp(
    val _id: String,
    @SerialName("client_app_id") val clientAppId: String,
    val name: String,
    @SerialName("domain_id") val domainId: String,
    @SerialName("group_id") val groupId: String
)

/**
 * Client to interact with App Services Server. It allows to create Applications and tweak their
 * configurations.
 */
class AppServicesClient(
    val baseUrl: String,
    private val groupUrl: String,
    private val httpClient: HttpClient,
    val dispatcher: CoroutineDispatcher,
) {
    companion object {
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
            return this@typedRequest.request(url) {
                this.method = method
                this.apply(block)
            }.bodyAsText()
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
            return this@typedListRequest.request(url) {
                this.method = method
                this.apply(block)
            }.bodyAsText()
                .let {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        ListSerializer(T::class.serializer()),
                        it
                    )
                }
        }

        suspend fun build(
            debug: Boolean,
            baseUrl: String,
            dispatcher: CoroutineDispatcher,
        ): AppServicesClient {
            val adminUrl = baseUrl + ADMIN_PATH
            // Work around issues on Native with the Ktor client being created and used
            // on different threads.
            // Log in using unauthorized client
            val unauthorizedClient = defaultClient("realm-baas-unauthorized", debug)
            val loginResponse = unauthorizedClient.typedRequest<LoginResponse>(
                HttpMethod.Post,
                "$adminUrl/auth/providers/local-userpass/login"
            ) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("username" to "unique_user@domain.com", "password" to "password"))
            }

            // Setup authorized client for the rest of the requests
            val accessToken = loginResponse.access_token
            unauthorizedClient.close()

            val httpClient = defaultClient("realm-baas-authorized", debug) {
                defaultRequest {
                    headers {
                        append("Authorization", "Bearer $accessToken")
                    }
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            prettyPrint = true
                            isLenient = true
                        }
                    )
                }
                install(Logging) {
                    // Set to LogLevel.ALL to debug Admin API requests. All relevant
                    // data for each request/response will be console or LogCat.
                    level = LogLevel.INFO
                }
            }

            // Collect app group id
            val groupId = httpClient.typedRequest<Profile>(Get, "$adminUrl/auth/profile")
                .roles
                .first()
                .group_id

            return AppServicesClient(
                baseUrl,
                "$adminUrl/groups/$groupId",
                httpClient,
                dispatcher
            )
        }
    }

    fun closeClient() {
        httpClient.close()
    }

    suspend fun getOrCreateApp(appName: String, initializer: suspend AppServicesClient.(app: BaasApp, service: Service) -> Unit): BaasApp =
        getApp(appName) ?: createApp(appName) {
            initialize(this, initializer)
        }

    private suspend fun getApp(appName: String): BaasApp? =
        withContext(dispatcher) {
            httpClient.typedListRequest<BaasApp>(Get, "$groupUrl/apps")
                .firstOrNull {
                    it.name == appName
                }
        }

    private suspend fun createApp(
        appName: String,
        initializer: suspend BaasApp.() -> Unit
    ): BaasApp =
        withContext(dispatcher) {
            httpClient.typedRequest<BaasApp>(Post, "$groupUrl/apps") {
                setBody(Json.parseToJsonElement("""{"name": $appName}"""))
                contentType(ContentType.Application.Json)
            }.apply {
                initializer(this)
            }
        }

    val BaasApp.url: String
        get() = "$groupUrl/apps/${this._id}"

    suspend fun BaasApp.sendPatchRequest(url: String, requestBody: String) =
        repeat(2) {
            httpClient.request("$baseUrl/app/${this.clientAppId}/endpoint/forwardAsPatch") {
                this.method = HttpMethod.Post
                setBody(
                    buildJsonObject {
                        put("url", url)
                        put("body", requestBody)
                    }
                )
                contentType(ContentType.Application.Json)
            }

            delay(1000)
        }

    suspend fun BaasApp.addFunction(function: Function): Function =
        withContext(dispatcher) {
            httpClient.typedRequest<Function>(
                Post,
                "$url/functions"
            ) {
                setBody(function)
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addSchema(schema: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/schemas"
            ) {
                setBody(Json.parseToJsonElement(schema))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addService(service: String): Service =
        withContext(dispatcher) {
            httpClient.typedRequest<Service>(
                Post,
                "$url/services"
            ) {
                setBody(Json.parseToJsonElement(service))
                contentType(ContentType.Application.Json)
            }.run {
                copy(app = this@addService)
            }
        }

    suspend fun BaasApp.addAuthProvider(authProvider: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/auth_providers"
            ) {
                setBody(Json.parseToJsonElement(authProvider))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.getAuthProvider(type: String): AuthProvider =
        withContext(dispatcher) {
            httpClient.typedListRequest<AuthProvider>(
                Get,
                "$url/auth_providers"
            )
        }.first {
            it.type == type
        }.run {
            copy(app = this@getAuthProvider)
        }

    suspend fun BaasApp.setDevelopmentMode(developmentModeEnabled: Boolean) =
        withContext(dispatcher) {
            httpClient.request("$url/sync/config") {
                this.method = HttpMethod.Put
                setBody(Json.parseToJsonElement("""{"development_mode_enabled": $developmentModeEnabled}"""))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.setCustomUserData(userDataConfig: String) =
        withContext(dispatcher) {
            sendPatchRequest(
                url = "$url/custom_user_data",
                requestBody = userDataConfig
            )
        }

    suspend fun BaasApp.addEndpoint(endpoint: String) =
        withContext(dispatcher) {
            httpClient.request("$url/endpoints") {
                this.method = HttpMethod.Post
                setBody(Json.parseToJsonElement(endpoint))
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun BaasApp.addSecret(secret: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/secrets"
            ) {
                setBody(Json.parseToJsonElement(secret))
                contentType(ContentType.Application.Json)
            }
        }

    val AuthProvider.url: String
        get() = "${app!!.url}/auth_providers/$_id"

    suspend fun AuthProvider.enable(enabled: Boolean) =
        withContext(dispatcher) {
            httpClient.request("$url/${if (enabled) "enable" else "disable"}") {
                this.method = HttpMethod.Put
            }
        }

    suspend fun AuthProvider.updateConfig(block: MutableMap<String, JsonElement>.() -> Unit) {
        mutableMapOf<String, JsonElement>().apply {
            block()
            app!!.sendPatchRequest(
                url,
                JsonObject(mapOf("config" to JsonObject(this))).toString()
            )
        }
    }

    val Service.url: String
        get() = "${app!!.url}/services/$_id"

    suspend fun Service.setSyncConfig(config: String) =
        withContext(dispatcher) {
            app!!.sendPatchRequest(
                url = "$url/config",
                requestBody = config
            )
        }

    suspend fun Service.addRule(rule: String): JsonObject =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonObject>(
                Post,
                "$url/rules"
            ) {
                setBody(Json.parseToJsonElement(rule))
                contentType(ContentType.Application.Json)
            }
        }

    /**
     * Deletes all currently registered and pending users on the App Services Application.
     */
    suspend fun BaasApp.deleteAllUsers() = withContext(dispatcher) {
        deleteAllRegisteredUsers()
        deleteAllPendingUsers()
    }

    private suspend fun BaasApp.deleteAllPendingUsers() {
        val pendingUsers = httpClient.typedRequest<JsonArray>(
            Get,
            "$url/user_registrations/pending_users"
        )
        for (pendingUser in pendingUsers) {
            val loginTypes = pendingUser.jsonObject["login_ids"]!!.jsonArray
            loginTypes
                .filter { it.jsonObject["id_type"]!!.jsonPrimitive.content == "email" }
                .map {
                    httpClient.delete(
                        "$url/user_registrations/by_email/${it.jsonObject["id"]!!.jsonPrimitive.content}"
                    )
                }
        }
    }

    private suspend fun BaasApp.deleteAllRegisteredUsers() {
        val users = httpClient.typedRequest<JsonArray>(
            Get,
            "$url/users"
        )
        users.map {
            httpClient.delete("$url/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
        }
    }

    val BaasApp.mongodbService: Service
        get() {
            return runBlocking {
                httpClient.typedListRequest<Service>(Get, "$url/services")
                    .first {
                        it.type == "mongodb"
                    }
            }
        }

    private suspend fun BaasApp.controlSync(
        serviceId: String,
        enabled: Boolean,
        permissions: SyncPermissions? = null
    ) {
        val url = "$url/services/$serviceId/config"
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
        sendPatchRequest(url, configObj.toString())
    }

    suspend fun BaasApp.pauseSync() =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id
            controlSync(backingDbServiceId, false)
        }

    suspend fun BaasApp.startSync() =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id
            controlSync(backingDbServiceId, true)
        }

    suspend fun BaasApp.triggerClientReset(userId: String) =
        withContext(dispatcher) {
            deleteDocument("__realm_sync", "clientfiles", """{"ownerId": "$userId"}""")
        }

    suspend fun BaasApp.triggerClientReset(syncMode: SyncMode, userId: String) =
        withContext(dispatcher) {
            when (syncMode) {
                SyncMode.PARTITION_BASED ->
                    deleteDocument("__realm_sync", "clientfiles", """{"ownerId": "$userId"}""")
                SyncMode.FLEXIBLE ->
                    deleteDocument("__realm_sync_$_id", "clientfiles", """{"ownerId": "$userId"}""")
            }
        }

    suspend fun BaasApp.changeSyncPermissions(permissions: SyncPermissions, block: () -> Unit) =
        withContext(dispatcher) {
            val backingDbServiceId = mongodbService._id

            // Execute test logic
            try {
                controlSync(backingDbServiceId, true, permissions)
                block.invoke()
            } finally {
                // Restore original permissions
                controlSync(backingDbServiceId, true, SyncPermissions(read = true, write = true))
            }
        }

    suspend fun BaasApp.getAuthConfigData(): String =
        withContext(dispatcher) {
            val providerId: String = getLocalUserPassProviderId()
            val url = "$url/auth_providers/$providerId"
            httpClient.typedRequest<JsonObject>(Get, url).toString()
        }

    suspend fun BaasApp.setAutomaticConfirmation(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("autoConfirm", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.setCustomConfirmation(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("runConfirmationFunction", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.setResetFunction(enabled: Boolean) =
        withContext(dispatcher) {
            getAuthProvider("local-userpass").updateConfig {
                put("runResetFunction", JsonPrimitive(enabled))
            }
        }

    suspend fun BaasApp.insertDocument(clazz: String, json: String): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "insertDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(clientAppId)
                    add(clazz)
                    add(Json.decodeFromString<JsonObject>(json))
                }
            )
        }

    suspend fun BaasApp.queryDocument(clazz: String, query: String): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "queryDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(clientAppId)
                    add(clazz)
                    add(query)
                }
            )
        }

    private suspend fun BaasApp.deleteDocument(
        db: String,
        clazz: String,
        query: String
    ): JsonObject? =
        withContext(dispatcher) {
            functionCall(
                name = "deleteDocument",
                arguments = buildJsonArray {
                    add(mongodbService.name)
                    add(db)
                    add(clazz)
                    add(query)
                }
            )
        }

    private suspend fun BaasApp.getLocalUserPassProviderId(): String =
        withContext(dispatcher) {
            httpClient.typedRequest<JsonArray>(
                Get,
                "$url/auth_providers"
            ).let { arr: JsonArray ->
                arr.firstOrNull { el: JsonElement ->
                    el.jsonObject["name"]!!.jsonPrimitive.content == "local-userpass"
                }?.let { el: JsonElement ->
                    el.jsonObject["_id"]?.jsonPrimitive?.content ?: throw IllegalStateException(
                        "Could not find '_id': $arr"
                    )
                } ?: throw IllegalStateException("Could not find local-userpass provider: $arr")
            }
        }

    private suspend fun BaasApp.functionCall(
        name: String,
        arguments: JsonArray
    ): JsonObject? =
        withContext(dispatcher) {
            val functionCall = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }

            val url =
                "$url/debug/execute_function?run_as_system=true"
            httpClient.typedRequest<JsonObject>(Post, url) {
                setBody(functionCall)
                contentType(ContentType.Application.Json)
            }.jsonObject["result"]!!.let {
                when (it) {
                    is JsonNull -> null
                    else -> it.jsonObject
                }
            }
        }
}
