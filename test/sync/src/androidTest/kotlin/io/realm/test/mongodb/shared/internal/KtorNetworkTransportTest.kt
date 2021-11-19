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
import io.realm.internal.platform.runBlocking
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.internal.util.use
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.channels.Channel
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BASE_URL = "http://127.0.0.1:8888" // URL to command server

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
    fun requestSuccessful() = runBlocking {
        val url = "$BASE_URL/okhttp?success=true"
        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "{ \"body\" : \"some content\" }"

            val response = Channel<Response>(1).use { channel ->
                transport.sendRequest(
                    method.nativeKey,
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySend(response) }
                channel.receive()
            }
            assertEquals(200, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.name}-success", response.body, "$method failed")
        }
    }

    @Test
    fun requestFailedOnServer() = runBlocking {
        val url = "$BASE_URL/okhttp?success=false"
        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "{ \"body\" : \"some content\" }"

            val response = Channel<Response>(1).use { channel ->
                transport.sendRequest(
                    method.nativeKey,
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySend(response) }
                channel.receive()
            }
            assertEquals(500, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.name}-failure", response.body, "$method failed")
        }
    }

    // Make sure that the client doesn't crash if attempting to send invalid JSON
    // This is mostly a guard against Java crashing if ObjectStore serializes the wrong
    // way by accident.
    @Test
    fun requestSendsIllegalJson() = runBlocking {
        val url = "$BASE_URL/okhttp?success=true"
        for (method in HTTPMethod.values()) {
            val body = if (method == HTTPMethod.GET) "" else "Boom!"

            val response = Channel<Response>(1).use { channel ->
                transport.sendRequest(
                    method.nativeKey,
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySend(response) }
                channel.receive()
            }
            assertEquals(200, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.name}-success", response.body, "$method failed")
        }
    }

    @Test
    @Ignore
    // TODO Need to ensure errors from network layers are propagated. Could be done by
    //  interrupting like on Java (OkHttpNetworkTransportTests.requestInterrupted), but could maybe
    //  be simpler without need to send signals in a platform agnostic way
    //  https://github.com/realm/realm-kotlin/issues/451
    fun errorPropagation() {
    }
}
