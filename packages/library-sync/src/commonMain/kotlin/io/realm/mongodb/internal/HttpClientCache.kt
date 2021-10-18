package io.realm.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.freeze

/**
 * Work-around for https://github.com/realm/realm-kotlin/issues/480
 *
 * This allows us to either cache the Client or re-create it pr. request
 * on a platform basis.
 */

fun createClient(timeoutMs: Long): HttpClient {
    // Need to freeze value as it is used inside the client's init lambda block, which also
    // freezes captured objects too, see:
    // https://youtrack.jetbrains.com/issue/KTOR-1223#focus=Comments-27-4618681.0-0
    val frozenTimeout = timeoutMs.freeze()
    // TODO We probably need to fix the clients, so ktor does not automatically override with
    //  another client if people update the runtime available ones through other dependencies
    return HttpClient() {
        // Charset defaults to UTF-8 (https://ktor.io/docs/http-plain-text.html#configuration)

        install(HttpTimeout) {
            connectTimeoutMillis = frozenTimeout
            requestTimeoutMillis = frozenTimeout
            socketTimeoutMillis = frozenTimeout
        }

        // TODO figure out logging and obfuscating sensitive info
        //  https://github.com/realm/realm-kotlin/issues/410
        install(Logging) {
            logger = object : Logger {
                // TODO Hook up with AppConfiguration/RealmConfiguration logger
                private val logger = createDefaultSystemLogger("realm-http")
                override fun log(message: String) {
                    logger.log(io.realm.log.LogLevel.DEBUG, throwable = null, message = message)
                }
            }
            level = LogLevel.BODY
        }

        followRedirects = true

        // TODO connectionPool?
    }
}

expect class HttpClientCache(timeoutMs: Long) {
    fun getClient(): HttpClient
}
