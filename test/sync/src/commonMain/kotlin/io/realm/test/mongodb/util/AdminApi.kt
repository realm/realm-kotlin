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

package io.realm.test.mongodb.util

import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.realm.internal.platform.runBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

private const val ADMIN_PATH = "/api/admin/v3.0"

/**
 * Wrapper around MongoDB Realm Server Admin functions needed for tests.
 */
interface AdminApi {

    /**
     * Creates a remote user. Only call this function when `EmailPasswordAuth.registerUser` doesn't
     * make sense to use.
     */
    fun createUserRestApi(email: String, password: String)

    /**
     * Deletes all currently registered and pending users on MongoDB Realm.
     */
    fun deleteAllUsers()

    /**
     * Terminate Sync on the server and re-enable it. Any existing sync sessions will throw an
     * error.
     */
    suspend fun restartSync()
}

open class AdminApiImpl internal constructor(
    baseUrl: String,
    private val appName: String,
    private val debug: Boolean,
    val dispatcher: CoroutineDispatcher
) : AdminApi {
    private val url = baseUrl + ADMIN_PATH
    private lateinit var loginResponse: LoginResponse
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

    // FIXME Do not rely on this API to create users and use EmailPasswordAuth wrapper is ready
    //  see https://github.com/realm/realm-kotlin/issues/433
    init {
        // Must be initialized on same thread as the constructor to allow initializing the lateinit
        // properties on native
        runBlocking(Dispatchers.Unconfined) {
            // Log in using unauthorized client
            loginResponse =
                defaultClient("$appName-unauthorized", debug).typedRequest<LoginResponse>(
                    HttpMethod.Post,
                    "$url/auth/providers/local-userpass/login"
                ) {
                    contentType(ContentType.Application.Json)
                    body = mapOf("username" to "unique_user@domain.com", "password" to "password")
                }

            // Setup authorized client for the rest of the requests
            val accessToken = loginResponse.access_token
            client = defaultClient("$appName-authorized", debug) {
                defaultRequest {
                    headers {
                        append("Authorization", "Bearer $accessToken")
                    }
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

    // Method to create remote user until we have proper EmailAuthProvider
    override fun createUserRestApi(email: String, password: String) {
        runBlocking(dispatcher) {
            client.post<Unit>("$url/groups/$groupId/apps/$appId/users") {
                contentType(ContentType.Application.Json)
                body = mapOf("email" to email, "password" to password)
            }
        }
    }

    /**
     * Deletes all currently registered and pending users on MongoDB Realm.
     */
    override fun deleteAllUsers() {
        deleteAllRegisteredUsers()
        deleteAllPendingUsers()
    }

    private fun deleteAllPendingUsers() {
        runBlocking(dispatcher) {
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
    }

    private fun deleteAllRegisteredUsers() {
        runBlocking(dispatcher) {
            val users = client.typedRequest<JsonArray>(
                Get,
                "$url/groups/$groupId/apps/$appId/users"
            )
            users.map {
                client.delete<Unit>("$url/groups/$groupId/apps/$appId/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
            }
        }
    }

    private suspend fun getBackingDBServiceId(): String =
        client.typedRequest<JsonArray>(Get, "$url/groups/$groupId/apps/$appId/services")
            .first()
            .let {
                it.jsonObject["_id"]!!.jsonPrimitive.content
            }

    @Suppress("TooGenericExceptionThrown")
    private suspend fun controlSync(serviceId: String, enabled: Boolean) =
        client.request<HttpResponse>("$url/groups/$groupId/apps/$appId/services/$serviceId/config") {
            method = Patch
            body = """
                    {
                      "sync": {
                        "state": "${if (enabled) "enabled" else "disabled"}",
                        "database_name": "test_data",
                        "partition": {
                          "key": "realm_id",
                          "type": "string",
                          "permissions": {
                            "read": true,
                            "write": true
                          }
                        },
                        "last_disabled": 1633520376
                      }
                    }
            """.trimIndent()
        }.let {
            if (!it.status.isSuccess())
                throw Exception("Failed to ${if (enabled) "enable" else "disable"} sync service.")
        }

    // These calls work but we should not use them to alter the state of a sync session as Ktor's
    // default HttpClient doesn't like PATCH requests on Native:
    // https://github.com/realm/realm-kotlin/issues/519
    override suspend fun restartSync() {
        withContext(dispatcher) {
            val backingDbServiceId = getBackingDBServiceId()
            controlSync(backingDbServiceId, false)
            controlSync(backingDbServiceId, true)
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
}
