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

package io.realm.test.mongodb.admin

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.runBlocking
import io.realm.log.LogLevel
import io.realm.mongodb.App
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private const val baseUrl = "http://127.0.0.1:9090/api/admin/v3.0"

/**
 * Wrapper around MongoDB Realm Server Admin functions needed for tests.
 */
class ServerAdmin(private val app: App) {
    private lateinit var loginResponse: LoginResponse
    private lateinit var groupId: String
    private lateinit var appId: String

    private lateinit var client: HttpClient

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
        runBlocking {
            // Log in using unauthorized client
            loginResponse =
                defaultClient("realm-http-admin-unauthorized").post("$baseUrl/auth/providers/local-userpass/login") {
                    contentType(ContentType.Application.Json)
                    body = mapOf("username" to "unique_user@domain.com", "password" to "password")
                }
            // Setup authorized client for the rest of the requests
            client = defaultClient("realm-http-admin-authorized") {
                defaultRequest {
                    headers {
                        append("Authorization", "Bearer ${loginResponse.access_token}")
                    }
                }
                install(Logging) {
                    level = io.ktor.client.features.logging.LogLevel.NONE
                }
            }
            // Collect app group id
            groupId = client.get<Profile>("$baseUrl/auth/profile").roles.first().group_id
            // Verify and set app id
            appId = client.get<List<ServerApp>>("$baseUrl/groups/$groupId/apps")
                .first { it.client_app_id == app.configuration.appId }._id
        }
    }

    // Method to create remote user until we have proper EmailAuthProvider
    fun createUser(email: String, password: String) {
        runBlocking {
            client.post<Unit>("$baseUrl/groups/$groupId/apps/$appId/users") {
                contentType(ContentType.Application.Json)
                body = mapOf("email" to email, "password" to password)
            }
        }
    }

    /**
     * Deletes all currently registered and pending users on MongoDB Realm.
     */
    fun deleteAllUsers() {
        runBlocking {
            deleteAllRegisteredUsers()
            deleteAllPendingUsers()
        }
    }

    private suspend fun deleteAllPendingUsers() {
        val pendingUsers =
            client.get<JsonArray>("$baseUrl/groups/$groupId/apps/$appId/user_registrations/pending_users")
        for (pendingUser in pendingUsers) {
            val loginTypes = pendingUser.jsonObject["login_ids"]!!.jsonArray
            loginTypes
                .filter { it.jsonObject["id_type"]!!.jsonPrimitive.content == "email" }
                .map {
                    client.delete<Unit>("$baseUrl/groups/$groupId/apps/$appId/user_registrations/by_email/${it.jsonObject["id"]!!.jsonPrimitive.content}")
                }
        }
    }

    private suspend fun deleteAllRegisteredUsers() {
        val users = client.get<JsonArray>("$baseUrl/groups/$groupId/apps/$appId/users")
        users.map {
            client.delete<Unit>("$baseUrl/groups/$groupId/apps/$appId/users/${it.jsonObject["_id"]!!.jsonPrimitive.content}")
        }
    }

    // TODO Consider moving it to util package?
    @OptIn(ExperimentalTime::class)
    private fun defaultClient(name: String, block: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
        // Need to freeze value as it is used inside the client's init lambda block, which also
        // freezes captured objects too, see:
        // https://youtrack.jetbrains.com/issue/KTOR-1223#focus=Comments-27-4618681.0-0
        val timeout = Duration.seconds(5).inWholeMilliseconds
        // TODO We probably need to fix the clients, so ktor does not automatically override with
        //  another client if people update the runtime available ones through other dependencies
        return HttpClient() {
            // Charset defaults to UTF-8 (https://ktor.io/docs/http-plain-text.html#configuration)
            install(HttpTimeout) {
                connectTimeoutMillis = timeout
                requestTimeoutMillis = timeout
                socketTimeoutMillis = timeout
            }

            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            // TODO figure out logging and obfuscating sensitive info
            //  https://github.com/realm/realm-kotlin/issues/410
            install(Logging) {
                logger = object : Logger {
                    // TODO Hook up with AppConfiguration/RealmConfiguration logger
                    private val logger = createDefaultSystemLogger(name)
                    override fun log(message: String) {
                        logger.log(LogLevel.DEBUG, throwable = null, message = message)
                    }
                }
                level = io.ktor.client.features.logging.LogLevel.ALL
            }

            followRedirects = true

            // TODO connectionPool?
            this.apply(block)
        }
    }

}
