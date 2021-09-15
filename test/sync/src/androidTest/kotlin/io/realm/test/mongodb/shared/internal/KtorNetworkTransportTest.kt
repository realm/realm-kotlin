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

package io.realm.test.mongodb.shared.internal

import io.realm.internal.interop.sync.Response
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BASE_URL = "http://127.0.0.1:8888" // URL to command server

@Ignore
internal class KtorNetworkTransportTest {

    private lateinit var transport: KtorNetworkTransport

    enum class HTTPMethod(val nativeKey: String) {
        GET("get"),
        POST("post"),
        PATCH("patch"),
        PUT("put"),
        DELETE("delete")
    }

    @BeforeTest
    fun setUp() {
        transport = KtorNetworkTransport(
            timeoutMs = 5000,
            dispatcher = singleThreadDispatcher("ktor-test")
        )
    }

    @Test
    fun requestSuccessful() {
        val url = "$BASE_URL/okhttp?success=true"

        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "{ \"body\" : \"some content\" }"

            val response = transport.sendRequest(
                method.nativeKey,
                url,
                mapOf(),
                body,
                true
            )
            assertEquals(200, response.httpResponseCode)
            assertEquals(0, response.customResponseCode)
            assertEquals("${method.name}-success", response.body)
        }
    }

    @Test
    fun requestFailedOnServer() {
        val url = "$BASE_URL/okhttp?success=false"
        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "{ \"body\" : \"some content\" }"

            val response = transport.sendRequest(
                method.nativeKey,
                url,
                mapOf(),
                body,
                true
            )
            assertEquals(500, response.httpResponseCode)
            assertEquals(0, response.customResponseCode)
            assertEquals("${method.name}-failure", response.body)
        }
    }

    // Make sure that the client doesn't crash if attempting to send invalid JSON
    // This is mostly a guard against Java crashing if ObjectStore serializes the wrong
    // way by accident.
    @Test
    fun requestSendsIllegalJson() {
        val url = "$BASE_URL/okhttp?success=true"
        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "Boom!"

            val response: Response = transport.sendRequest(
                method.nativeKey,
                url,
                mapOf(),
                body,
                true
            )
            assertEquals(200, response.httpResponseCode)
            assertEquals(0, response.customResponseCode)
            assertEquals("${method.name}-success", response.body)
        }
    }

    @Test
    @Ignore
    // TODO Need to ensure errors from network layers are propagated. Could be done by
    //  interrupting like on Java (OkHttpNetworkTransportTests.requestInterrupted), but could maybe
    //  be simpler without need to send signals in a platform agnostic way
    fun errorPropagation() {
    }
}
