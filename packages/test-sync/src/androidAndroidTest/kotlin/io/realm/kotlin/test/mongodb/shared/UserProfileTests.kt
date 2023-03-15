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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.Response
import io.realm.kotlin.internal.interop.sync.ResponseCallback
import io.realm.kotlin.mongodb.ext.profile
import io.realm.kotlin.mongodb.ext.profileAsBsonDocument
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

@Serializable
data class UserProfile(
    val name: String,
    val email: String,
    @SerialName("picture_url") val pictureUrl: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val gender: String,
    val birthday: String,
    @SerialName("min_age") val minAge: Long,
    @SerialName("max_age") val maxAge: Long,
)

class UserProfileTests {
    companion object {
        const val ACCESS_TOKEN =
            """eyJhbGciOiJSUzI1NiIsImtpZCI6IjVlNjk2M2RmYWZlYTYzMjU0NTgxYzAyNiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODM5NjcyMDgsImlhdCI6MTU4Mzk2NTQwOCwiaXNzIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWEzIiwic3RpdGNoX2RldklkIjoiMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwIiwic3RpdGNoX2RvbWFpbklkIjoiNWU2OTYzZGVhZmVhNjMyNTQ1ODFjMDI1Iiwic3ViIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWExIiwidHlwIjoiYWNjZXNzIn0.J4mp8LnlsxTQRV_7W2Er4qY0tptR76PJGG1k6HSMmUYqgfpJC2Fnbcf1VCoebzoNolH2-sr8AHDVBBCyjxRjqoY9OudFHmWZKmhDV1ysxPP4XmID0nUuN45qJSO8QEAqoOmP1crXjrUZWedFw8aaCZE-bxYfvcDHyjBcbNKZqzawwUw2PyTOlrNjgs01k2J4o5a5XzYkEsJuzr4_8UqKW6zXvYj24UtqnqoYatW5EzpX63m2qig8AcBwPK4ZHb5wEEUdf4QZxkRY5QmTgRHP8SSqVUB_mkHgKaizC_tSB3E0BekaDfLyWVC1taAstXJNfzgFtLI86AzuXS2dCiCfqQ"""
        const val REFRESH_TOKEN =
            """eyJhbGciOiJSUzI1NiIsImtpZCI6IjVlNjk2M2RmYWZlYTYzMjU0NTgxYzAyNiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODkxNDk0MDgsImlhdCI6MTU4Mzk2NTQwOCwic3RpdGNoX2RhdGEiOm51bGwsInN0aXRjaF9kZXZJZCI6IjAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMCIsInN0aXRjaF9kb21haW5JZCI6IjVlNjk2M2RlYWZlYTYzMjU0NTgxYzAyNSIsInN0aXRjaF9pZCI6IjVlNjk2NGUwYWZlYTYzMjU0NTgxYzFhMyIsInN0aXRjaF9pZGVudCI6eyJpZCI6IjVlNjk2NGUwYWZlYTYzMjU0NTgxYzFhMC1oaWF2b3ZkbmJxbGNsYXBwYnl1cmJpaW8iLCJwcm92aWRlcl90eXBlIjoiYW5vbi11c2VyIiwicHJvdmlkZXJfaWQiOiI1ZTY5NjNlMGFmZWE2MzI1NDU4MWMwNGEifSwic3ViIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWExIiwidHlwIjoicmVmcmVzaCJ9.FhLdpmL48Mw0SyUKWuaplz3wfeS8TCO8S7I9pIJenQww9nPqQ7lIvykQxjCCtinGvsZIJKt_7R31xYCq4Jp53Nw81By79IwkXtO7VXHPsXXZG5_2xV-s0u44e85sYD5su_H-xnx03sU2piJbWJLSB8dKu3rMD4mO-S0HNXCCAty-JkYKSaM2-d_nS8MNb6k7Vfm7y69iz_uwHc-bb_1rPg7r827K6DEeEMF41Hy3Nx1kCdAUOM9-6nYv3pZSU1PFrGYi2uyTXPJ7R7HigY5IGHWd0hwONb_NUr4An2omqfvlkLEd77ut4V9m6mExFkoKzRz7shzn-IGkh3e4h7ECGA"""
        const val USER_ID = "5e6964e0afea63254581c1a1"
        const val DEVICE_ID = "000000000000000000000000"

        val userProfile = UserProfile(
            name = "NAME",
            email = "unique_user@domain.com",
            pictureUrl = "PICTURE_URL",
            firstName = "FIRST_NAME",
            lastName = "LAST_NAME",
            gender = "GENDER",
            birthday = "BIRTHDAY",
            minAge = 1L,
            maxAge = 99L,
        )
    }

    private lateinit var app: TestApp
    lateinit var profileBody: Map<String, String>

    private fun setDefaultProfile() {
        profileBody = mapOf(
            "name" to userProfile.name,
            "email" to userProfile.email,
            "picture_url" to userProfile.pictureUrl,
            "first_name" to userProfile.firstName,
            "last_name" to userProfile.lastName,
            "gender" to userProfile.gender,
            "birthday" to userProfile.birthday,
            "min_age" to "${userProfile.minAge}",
            "max_age" to "${userProfile.maxAge}"
        )
    }

    private fun setEmptyProfile() {
        profileBody = mapOf()
    }

    @BeforeTest
    fun setUp() {
        app = TestApp(
            networkTransport = object : NetworkTransport {
                override val authorizationHeaderName: String?
                    get() = ""
                override val customHeaders: Map<String, String>
                    get() = mapOf()

                override fun sendRequest(
                    method: String,
                    url: String,
                    headers: Map<String, String>,
                    body: String,
                    callback: ResponseCallback
                ) {
                    val result: String = when {
                        url.endsWith("/providers/local-userpass/login") ->
                            """
                            {
                                "access_token": "$ACCESS_TOKEN",
                                "refresh_token": "$REFRESH_TOKEN",
                                "user_id": "$USER_ID",
                                "device_id": "$DEVICE_ID"
                            }            
                            """.trimIndent()

                        url.endsWith("/auth/profile") ->
                            """
                            {
                                "user_id": "5e6964e0afea63254581c1a1",
                                "domain_id": "000000000000000000000000",
                                "identities": [
                                    {
                                        "id": "5e68f51ade5ba998bb17500d",
                                        "provider_type": "local-userpass",
                                        "provider_id": "000000000000000000000003",
                                        "provider_data": {
                                            "email": "unique_user@domain.com"
                                        }
                                    }
                                ],
                                "data": ${Json.encodeToString(profileBody)},
                                "type": "normal",
                                "roles": [
                                    {
                                        "role_name": "GROUP_OWNER",
                                        "group_id": "5e68f51e087b1b33a53f56d5"
                                    }
                                ]
                            }
                            """.trimIndent()

                        url.endsWith("/location") ->
                            """
                            { "deployment_model" : "GLOBAL",
                              "location": "US-VA", 
                              "hostname": "http://localhost:9090",
                              "ws_hostname": "ws://localhost:9090"
                            }
                            """.trimIndent()
                        url.endsWith("/providers/local-userpass/register") ||
                                url.endsWith("auth/session") -> ""
                        else -> fail("Unexpected request url: $url")
                    }
                    callback.response(
                        Response(
                            httpResponseCode = 200,
                            customResponseCode = 0,
                            headers = mapOf("Content-Type" to "application/json"),
                            body = result
                        )
                    )
                }

                override fun close() = Unit
            }
        )
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun profileAsBsonDocument() {
        setDefaultProfile()
        val user = app.createUserAndLogin()
        val document = user.profileAsBsonDocument()

        assertEquals(profileBody.keys.size, document.keys.size)

        document.entries.forEach { (key: String, value: BsonValue) ->
            assertContains(profileBody.keys, key)
            val stringValue = when (value.bsonType) {
                BsonType.STRING -> value.asString().value
                BsonType.INT64 -> value.asInt64().value.toString()
                else -> TODO()
            }
            assertEquals(profileBody[key], stringValue, "failed comparing key $key")
        }
    }

    @Test
    fun profile() {
        setDefaultProfile()

        val user = app.createUserAndLogin()
        val userProfile = user.profile<UserProfile>()

        assertEquals(Companion.userProfile, userProfile)
    }

    @Test
    fun profileEmpty() {
        setEmptyProfile()

        val user = app.createUserAndLogin()
        assertFailsWith<SerializationException> {
            // TODO review as it fails because of missing fields. should it break like this?
            user.profile<UserProfile>()
        }
    }
}
