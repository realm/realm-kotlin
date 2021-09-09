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

package io.realm.mongodb.internal

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.realm.internal.platform.freeze
import io.realm.internal.platform.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import io.realm.interop.NetworkTransport
import io.realm.interop.Response

//@SharedImmutable
//internal expect val SyncDispatcher: CoroutineDispatcher

/**
 * TODO
 */
class KtorNetworkTransport(
    override val authorizationHeaderName: String? = null,
    override val customHeaders: Map<String, String> = mapOf(),
    private val timeoutMs: Long,
    private val dispatcher: CoroutineDispatcher,
) : NetworkTransport {

    private val client: HttpClient = getClient()

    override fun sendRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
        usesRefreshToken: Boolean
    ): Response {
        try {
            return runBlocking(dispatcher) {
                val requestBuilderBlock: HttpRequestBuilder.() -> Unit = {
                    headers {
                        // 1. First of all add all custom headers
                        customHeaders.forEach {
                            append(it.key, it.value)
                        }

                        // 2. Then replace default authorization header with custom one if present
                        headers.toMutableMap().also { receivedHeaders ->
                            val authorizationHeaderValue =
                                headers[DEFAULT_AUTHORIZATION_HEADER_NAME]
                            if (authorizationHeaderValue != null && DEFAULT_AUTHORIZATION_HEADER_NAME != authorizationHeaderName) {
                                receivedHeaders.remove(DEFAULT_AUTHORIZATION_HEADER_NAME)
                                receivedHeaders[authorizationHeaderName!!] =
                                    authorizationHeaderValue
                            }

                            // 3. Finally add all headers defined by Object Store
                            receivedHeaders.forEach {
                                append(it.key, it.value)
                            }
                        }
                    }

                    addBody(method, body)
                    addMethod(method)
                }

                try {
                    when (method) {
                        "delete" -> client.delete<HttpResponse>(url, requestBuilderBlock)
                        "patch" -> client.patch<HttpResponse>(url, requestBuilderBlock)
                        "post" -> client.post<HttpResponse>(url, requestBuilderBlock)
                        "put" -> client.put<HttpResponse>(url, requestBuilderBlock)
                        "get" -> client.get<HttpResponse>(url, requestBuilderBlock)
                        else -> throw IllegalArgumentException("Wrong request method: '$method'")
                    }.let {
                        processHttpResponse(it)
                    }
                } catch (e: ServerResponseException) {
                    // 500s are thrown as ServerResponseException
                    processHttpResponse(e.response)
                } catch (e: CancellationException) {
                    println("EXCEPTION 1: $e")
                    println("MESSAGE 1: ${e.message}")
                    Response(-1, -1, mapOf(), "")
                } catch (e: Exception) {
                    println("EXCEPTION 2: $e")
                    println("MESSAGE 2: ${e.message}")
                    Response(-2, -2, mapOf(), "")
                }
            }
        } catch (e: Exception) {
            println("EXCEPTION 3: $e")
            println("MESSAGE 3: ${e.message}")
            return Response(-3, -3, mapOf(), "")
        }
    }

    private suspend fun processHttpResponse(response: HttpResponse): Response {
        val responseBody = response.receive<String>()
        val responseStatusCode = response.status.value
        val responseHeaders = parseHeaders(response.headers)
        return httpResponse(responseStatusCode, responseHeaders, responseBody)
    }

    private fun HttpRequestBuilder.addBody(method: String, body: String) {
        when (method) {
            "delete", "patch", "post", "put" -> this.body = body
        }
    }

    private fun HttpRequestBuilder.addMethod(method: String) {
        when (method) {
            "delete" -> this.method = HttpMethod.Delete
            "patch" -> this.method = HttpMethod.Patch
            "post" -> this.method = HttpMethod.Post
            "put" -> this.method = HttpMethod.Put
            "get" -> this.method = HttpMethod.Get
        }
    }

    private fun getClient(): HttpClient {
        // Need to freeze value as it is used inside the client's init lambda block, which also
        // freezes captured objects too, see:
        // https://youtrack.jetbrains.com/issue/KTOR-1223#focus=Comments-27-4618681.0-0
        val frozenTimeout = timeoutMs.freeze()
        return HttpClient(CIO) {
            // Charset defaults to UTF-8 (https://ktor.io/docs/http-plain-text.html#configuration)
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json)
            }

            install(HttpTimeout) {
                connectTimeoutMillis = frozenTimeout
                requestTimeoutMillis = frozenTimeout
                socketTimeoutMillis = frozenTimeout
            }

            // TODO figure out logging and obduscating sensitive info
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
            }

            followRedirects = true

            // TODO connectionPool?
        }
    }

    private fun parseHeaders(headers: Headers): Map<String, String> {
        val parsedHeaders: MutableMap<String, String> = mutableMapOf()
        for (key in headers.names()) {
            parsedHeaders[key] = requireNotNull(headers[key]) { "Headeer '$key' cannot be null" }
        }
        return parsedHeaders
    }

    companion object {

        const val DEFAULT_AUTHORIZATION_HEADER_NAME = "Authorization"

        // Custom error codes. These must not match any HTTP response error codes
        const val ERROR_IO = 1000
        const val ERROR_INTERRUPTED = 1001
        const val ERROR_UNKNOWN = 1002

        private fun httpResponse(
            responseStatusCode: Int,
            responseHeaders: Map<String, String>,
            responseBody: String
        ): Response = Response(responseStatusCode, 0, responseHeaders, responseBody)
    }
}
