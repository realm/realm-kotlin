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
@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.test.mongodb.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.mongodb.internal.LogObfuscatorImpl
import io.realm.kotlin.mongodb.internal.createPlatformClient
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

// TODO Consider moving it to util package?
fun defaultClient(name: String, debug: Boolean, block: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
    val timeout = 60.seconds.inWholeMilliseconds
    return createPlatformClient {
        // Charset defaults to UTF-8 (https://ktor.io/docs/http-plain-text.html#configuration)
        install(HttpTimeout) {
            connectTimeoutMillis = timeout
            requestTimeoutMillis = timeout
            socketTimeoutMillis = timeout
        }

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }

        if (debug) {
            install(Logging) {
                logger = object : Logger {
                    private val logger = ContextLogger(name)
                    override fun log(message: String) {
                        logger.debug(LogObfuscatorImpl.obfuscate(message))
                    }
                }
                level = io.ktor.client.plugins.logging.LogLevel.ALL
            }
        }

        // We should allow redirects for all types, not just GET and HEAD
        // See https://github.com/ktorio/ktor/issues/1793
        install(HttpRedirect) {
            checkHttpMethod = false
        }

        // TODO connectionPool?
        this.apply(block)
    }
}
