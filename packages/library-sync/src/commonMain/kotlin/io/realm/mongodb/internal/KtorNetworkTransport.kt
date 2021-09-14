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

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.realm.internal.platform.freeze
import io.realm.internal.platform.runBlocking
import io.realm.internal.interop.sync.NetworkTransport
import io.realm.internal.interop.sync.Response
import io.realm.mongodb.AppConfiguration.Companion.DEFAULT_AUTHORIZATION_HEADER_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.forEach
import kotlin.collections.mapOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.toMutableMap

class KtorNetworkTransport(
    override val authorizationHeaderName: String = DEFAULT_AUTHORIZATION_HEADER_NAME,
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
            // FIXME Ensure background jobs does not block user/main thread
            //  https://github.com/realm/realm-kotlin/issues/450
            return runBlocking(dispatcher) {
                val requestBuilderBlock: HttpRequestBuilder.() -> Unit = {
                    headers {
                        // 1. First of all add all custom headers
                        customHeaders.forEach {
                            append(it.key, it.value)
                        }

                        // 2. Then replace default authorization header with custom one if present
                        headers
                            .toMutableMap().also { receivedHeaders ->
                                val authorizationHeaderValue =
                                    headers[DEFAULT_AUTHORIZATION_HEADER_NAME]

                                if (authorizationHeaderValue != null &&
                                    authorizationHeaderName != DEFAULT_AUTHORIZATION_HEADER_NAME
                                ) {
                                    receivedHeaders.remove(DEFAULT_AUTHORIZATION_HEADER_NAME)
                                    receivedHeaders[authorizationHeaderName] =
                                        authorizationHeaderValue
                                }

                                // 3. Finally add all headers defined by Object Store
                                receivedHeaders.forEach {
                                    if (method != "get"
                                        || !url.contains("profile")
                                        || it.key != HttpHeaders.ContentType
                                    ) {
                                        append(it.key, it.value)
                                    }
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
        return createHttpResponse(responseStatusCode, responseHeaders, responseBody)
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

            install(HttpTimeout) {
                connectTimeoutMillis = frozenTimeout
                requestTimeoutMillis = frozenTimeout
                socketTimeoutMillis = frozenTimeout
            }

            // TODO figure out logging and obfuscating sensitive info
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
            parsedHeaders[key] = requireNotNull(headers[key]) { "Header '$key' cannot be null" }
        }
        return parsedHeaders
    }

    companion object {
        // Custom error codes. These must not match any HTTP response error codes
        const val ERROR_IO = 1000
        const val ERROR_INTERRUPTED = 1001
        const val ERROR_UNKNOWN = 1002

        private fun createHttpResponse(
            responseStatusCode: Int,
            responseHeaders: Map<String, String>,
            responseBody: String
        ): Response = Response(responseStatusCode, 0, responseHeaders, responseBody)
    }
}
