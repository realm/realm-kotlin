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

package io.realm.kotlin.mongodb.internal

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.logging.Logger
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
import io.ktor.util.InternalAPI
import io.ktor.utils.io.errors.IOException
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.Response
import io.realm.kotlin.internal.interop.sync.ResponseCallback
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.internal.util.DispatcherHolder
import io.realm.kotlin.mongodb.AppConfiguration.Companion.DEFAULT_AUTHORIZATION_HEADER_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlin.collections.set

public class KtorNetworkTransport(
    override val authorizationHeaderName: String = DEFAULT_AUTHORIZATION_HEADER_NAME,
    override val customHeaders: Map<String, String> = mapOf(),
    // FIXME Rework timeout to take a Duration instead
    //  https://github.com/realm/realm-kotlin/issues/408
    timeoutMs: Long,
    dispatcherFactory: CoroutineDispatcherFactory,
    logger: Logger? = null,
) : NetworkTransport {

    private val dispatcherHolder: DispatcherHolder = dispatcherFactory.create()
    private val clientCache: HttpClientCache = HttpClientCache(timeoutMs, logger)

    @Suppress("ComplexMethod", "TooGenericExceptionCaught")
    override fun sendRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
        callback: ResponseCallback,
    ) {
        val client = clientCache.getClient()
        CoroutineScope(dispatcherHolder.dispatcher).async {
            val response = try {
                val requestBuilderBlock: HttpRequestBuilder.() -> Unit = {
                    headers {
                        // 1. First of all add all custom headers
                        customHeaders.forEach {
                            append(it.key, it.value)
                        }

                        // 2. Then add all headers received from OS
                        headers.forEach { (key, value) ->
                            // It is not allowed to set content type on gets https://github.com/ktorio/ktor/issues/1127
                            if (method != "get" || key != HttpHeaders.ContentType) {
                                append(key, value)
                            }
                        }

                        // 3. Finally, if we have a non-default auth header name, replace the OS
                        // default with the custom one
                        if (authorizationHeaderName != DEFAULT_AUTHORIZATION_HEADER_NAME &&
                            contains(DEFAULT_AUTHORIZATION_HEADER_NAME)
                        ) {
                            this[DEFAULT_AUTHORIZATION_HEADER_NAME]?.let { originalAuthValue ->
                                this[authorizationHeaderName] = originalAuthValue
                            }
                            this.remove(DEFAULT_AUTHORIZATION_HEADER_NAME)
                        }
                    }

                    addBody(method, body)
                    addMethod(method)
                }
                when (method) {
                    "delete" -> client.delete(url, requestBuilderBlock)
                    "patch" -> client.patch(url, requestBuilderBlock)
                    "post" -> client.post(url, requestBuilderBlock)
                    "put" -> client.put(url, requestBuilderBlock)
                    "get" -> client.get(url, requestBuilderBlock)
                    else -> throw IllegalArgumentException("Wrong request method: '$method'")
                }.let {
                    processHttpResponse(it)
                }
            } catch (e: ClientRequestException) {
                processHttpResponse(e.response)
            } catch (e: ServerResponseException) {
                // 500s are thrown as ServerResponseException
                processHttpResponse(e.response)
            } catch (e: IOException) {
                Response(0, ERROR_IO, mapOf(), e.toString())
            } catch (e: CancellationException) {
                Response(0, ERROR_INTERRUPTED, mapOf(), e.toString())
            } catch (e: Exception) {
                Response(0, ERROR_UNKNOWN, mapOf(), e.toString())
            }
            callback.response(response)
        }
    }

    override fun close() {
        clientCache.close()
        dispatcherHolder.close()
    }

    private suspend fun processHttpResponse(response: HttpResponse): Response {
        val responseBody = response.body<String>()
        val responseStatusCode = response.status.value
        val responseHeaders = parseHeaders(response.headers)
        return createHttpResponse(responseStatusCode, responseHeaders, responseBody)
    }

    @OptIn(InternalAPI::class)
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

    private fun parseHeaders(headers: Headers): Map<String, String> {
        val parsedHeaders: MutableMap<String, String> = mutableMapOf()
        for (key in headers.names()) {
            parsedHeaders[key] = requireNotNull(headers[key]) { "Header '$key' cannot be null" }
        }
        return parsedHeaders
    }

    public companion object {
        // Custom error codes. These must not match any HTTP response error codes
        public const val ERROR_IO: Int = 1000
        public const val ERROR_INTERRUPTED: Int = 1001
        public const val ERROR_UNKNOWN: Int = 1002

        private fun createHttpResponse(
            responseStatusCode: Int,
            responseHeaders: Map<String, String>,
            responseBody: String
        ): Response = Response(responseStatusCode, 0, responseHeaders, responseBody)
    }
}
