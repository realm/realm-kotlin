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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.realm.internal.platform.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val baseUrl = "http://127.0.0.1:9090/api/admin/v3.0"

/**
 * Wrapper around MongoDB Realm Server Admin functions needed for tests.
 */
interface AdminApi {
    // Method to create remote user until we have proper EmailAuthProvider
    fun createUser(email: String, password: String)

    /**
     * Deletes all currently registered and pending users on MongoDB Realm.
     */
    fun deleteAllUsers()
}

open class AdminApiImpl internal constructor(private val appName: String) : AdminApi {
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
            }
            // Collect app group id
            groupId = client.get<Profile>("$baseUrl/auth/profile").roles.first().group_id
            // Verify app id
            appId = client.get<List<ServerApp>>("$baseUrl/groups/$groupId/apps")
                .firstOrNull { it.client_app_id == appName }?._id ?: error("App ${appName} not found")
        }
    }

    // Method to create remote user until we have proper EmailAuthProvider
    override fun createUser(email: String, password: String) {
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
    override fun deleteAllUsers() {
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
}
