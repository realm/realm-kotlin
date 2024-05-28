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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.realm.kotlin.test.mongodb.common.internal

import io.ktor.http.HttpMethod
import io.realm.kotlin.internal.interop.sync.Response
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.internal.KtorNetworkTransport
import io.realm.kotlin.test.mongodb.TEST_SERVER_BASE_URL
import io.realm.kotlin.test.mongodb.syncServerAppName
import io.realm.kotlin.test.mongodb.util.AppServicesClient
import io.realm.kotlin.test.mongodb.util.BaasApp
import io.realm.kotlin.test.mongodb.util.KtorTestAppInitializer.initialize
import io.realm.kotlin.test.mongodb.util.Service
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.trySendOrFail
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

val TEST_METHODS = listOf(
    HttpMethod.Get,
    HttpMethod.Post,
    HttpMethod.Patch,
    HttpMethod.Put,
    HttpMethod.Delete,
)

internal class KtorNetworkTransportTest {
    private lateinit var transport: KtorNetworkTransport
    private lateinit var endpoint: String

    // Delete method must have an empty body or the server app fails to process it.
    private val emptyBodyMethods = setOf(HttpMethod.Get, HttpMethod.Delete)
    private lateinit var dispatcher: CloseableCoroutineDispatcher
    private lateinit var appClient: AppServicesClient

    @BeforeTest
    fun setUp() {
        dispatcher = singleThreadDispatcher("test-ktor-dispatcher")
        val dispatcherFactory = CoroutineDispatcherFactory.unmanaged(dispatcher)

        transport = KtorNetworkTransport(
            timeoutMs = 60000,
            dispatcherHolder = dispatcherFactory.create()
        )

        val app = runBlocking(dispatcher) {
            appClient = AppServicesClient.build(
                baseUrl = TEST_SERVER_BASE_URL,
                debug = false,
                dispatcher = dispatcher
            )
            appClient.run {
                getOrCreateApp(syncServerAppName("ktor")) { app: BaasApp, service: Service ->
                    initialize(app, TEST_METHODS)
                }
            }
        }

        endpoint = "$TEST_SERVER_BASE_URL/app/${app.clientAppId}/endpoint/test_network_transport"
    }

    @AfterTest
    fun tearDown() {
        transport.close()
        dispatcher.close()
        appClient.closeClient()
    }

    @Test
    fun requestSuccessful() = runBlocking {
        val url = "$endpoint?success=true"
        for (method in TEST_METHODS) {
            val body = if (emptyBodyMethods.contains(method)) "" else "{ \"body\" : \"some content\" }"
            val response = TestChannel<Response>().use { channel ->
                transport.sendRequest(
                    method.value.lowercase(),
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySendOrFail(response) }
                channel.receiveOrFail()
            }
            assertEquals(200, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.value}-success", response.body, "$method failed")
        }
    }

    @Test
    fun requestFailedOnServer() = runBlocking {
        val url = "$endpoint?success=false"
        for (method in TEST_METHODS) {
            val body = if (emptyBodyMethods.contains(method)) "" else "{ \"body\" : \"some content\" }"

            val response = TestChannel<Response>().use { channel ->
                transport.sendRequest(
                    method.value.lowercase(),
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySendOrFail(response) }
                channel.receiveOrFail()
            }
            assertEquals(500, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.value}-failure", response.body, "$method failed")
        }
    }

    // Make sure that the client doesn't crash if attempting to send invalid JSON
    // This is mostly a guard against Java crashing if ObjectStore serializes the wrong
    // way by accident.
    @Test
    fun requestSendsIllegalJson() = runBlocking {
        val url = "$endpoint?success=true"
        for (method in TEST_METHODS) {
            val body = if (emptyBodyMethods.contains(method)) "" else "Boom!"

            val response = TestChannel<Response>().use { channel ->
                transport.sendRequest(
                    method.value.lowercase(),
                    url,
                    mapOf(),
                    body
                ) { response -> channel.trySendOrFail(response) }
                channel.receiveOrFail()
            }
            assertEquals(200, response.httpResponseCode, "$method failed")
            assertEquals(0, response.customResponseCode, "$method failed")
            assertEquals("${method.value}-success", response.body, "$method failed")
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
