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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.Response
import io.realm.kotlin.internal.interop.sync.ResponseCallback
import io.realm.kotlin.mongodb.ext.profileAsBsonDocument
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

class UserProfileTests {
    companion object {
        const val ACCESS_TOKEN =
            """eyJhbGciOiJSUzI1NiIsImtpZCI6IjVlNjk2M2RmYWZlYTYzMjU0NTgxYzAyNiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODM5NjcyMDgsImlhdCI6MTU4Mzk2NTQwOCwiaXNzIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWEzIiwic3RpdGNoX2RldklkIjoiMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwIiwic3RpdGNoX2RvbWFpbklkIjoiNWU2OTYzZGVhZmVhNjMyNTQ1ODFjMDI1Iiwic3ViIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWExIiwidHlwIjoiYWNjZXNzIn0.J4mp8LnlsxTQRV_7W2Er4qY0tptR76PJGG1k6HSMmUYqgfpJC2Fnbcf1VCoebzoNolH2-sr8AHDVBBCyjxRjqoY9OudFHmWZKmhDV1ysxPP4XmID0nUuN45qJSO8QEAqoOmP1crXjrUZWedFw8aaCZE-bxYfvcDHyjBcbNKZqzawwUw2PyTOlrNjgs01k2J4o5a5XzYkEsJuzr4_8UqKW6zXvYj24UtqnqoYatW5EzpX63m2qig8AcBwPK4ZHb5wEEUdf4QZxkRY5QmTgRHP8SSqVUB_mkHgKaizC_tSB3E0BekaDfLyWVC1taAstXJNfzgFtLI86AzuXS2dCiCfqQ"""
        const val REFRESH_TOKEN =
            """eyJhbGciOiJSUzI1NiIsImtpZCI6IjVlNjk2M2RmYWZlYTYzMjU0NTgxYzAyNiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1ODkxNDk0MDgsImlhdCI6MTU4Mzk2NTQwOCwic3RpdGNoX2RhdGEiOm51bGwsInN0aXRjaF9kZXZJZCI6IjAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMCIsInN0aXRjaF9kb21haW5JZCI6IjVlNjk2M2RlYWZlYTYzMjU0NTgxYzAyNSIsInN0aXRjaF9pZCI6IjVlNjk2NGUwYWZlYTYzMjU0NTgxYzFhMyIsInN0aXRjaF9pZGVudCI6eyJpZCI6IjVlNjk2NGUwYWZlYTYzMjU0NTgxYzFhMC1oaWF2b3ZkbmJxbGNsYXBwYnl1cmJpaW8iLCJwcm92aWRlcl90eXBlIjoiYW5vbi11c2VyIiwicHJvdmlkZXJfaWQiOiI1ZTY5NjNlMGFmZWE2MzI1NDU4MWMwNGEifSwic3ViIjoiNWU2OTY0ZTBhZmVhNjMyNTQ1ODFjMWExIiwidHlwIjoicmVmcmVzaCJ9.FhLdpmL48Mw0SyUKWuaplz3wfeS8TCO8S7I9pIJenQww9nPqQ7lIvykQxjCCtinGvsZIJKt_7R31xYCq4Jp53Nw81By79IwkXtO7VXHPsXXZG5_2xV-s0u44e85sYD5su_H-xnx03sU2piJbWJLSB8dKu3rMD4mO-S0HNXCCAty-JkYKSaM2-d_nS8MNb6k7Vfm7y69iz_uwHc-bb_1rPg7r827K6DEeEMF41Hy3Nx1kCdAUOM9-6nYv3pZSU1PFrGYi2uyTXPJ7R7HigY5IGHWd0hwONb_NUr4An2omqfvlkLEd77ut4V9m6mExFkoKzRz7shzn-IGkh3e4h7ECGA"""
        const val USER_ID = "5e6964e0afea63254581c1a1"
        const val DEVICE_ID = "000000000000000000000000"
        const val NAME = "NAME"
        const val EMAIL = "unique_user@domain.com"
        const val PICTURE_URL = "PICTURE_URL"
        const val FIRST_NAME = "FIRST_NAME"
        const val LAST_NAME = "LAST_NAME"
        const val GENDER = "GENDER"
        const val BIRTHDAY = "BIRTHDAY"
        const val MIN_AGE = 1L
        const val MAX_AGE = 99L
    }

    private lateinit var app: TestApp
    lateinit var profileBody: Map<String, String>

    private fun setDefaultProfile() {
        profileBody = mapOf(
            "name" to NAME,
            "email" to EMAIL,
            "picture_url" to PICTURE_URL,
            "first_name" to FIRST_NAME,
            "last_name" to LAST_NAME,
            "gender" to GENDER,
            "birthday" to BIRTHDAY,
            "min_age" to "$MIN_AGE",
            "max_age" to "$MAX_AGE"
        )
    }

    private fun setNullProfile() {
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
    fun profile() {
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

    @kotlinx.serialization.Serializable
    class SerializableClass

    @Test
    fun unsupportedType() {
        setDefaultProfile()
        val user = app.createUserAndLogin()

        assertFailsWithMessage<IllegalArgumentException>("Only BsonDocuments are valid return types") {
            user.profile(BsonEncoder.serializersModule.serializer<SerializableClass>())
        }
    }
}
