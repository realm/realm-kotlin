package io.realm.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.features.logging.Logger

/**
 * On MacOS we need to re-create the Client on each request due to
 * https://github.com/realm/realm-kotlin/issues/480.
 */
actual class HttpClientCache actual constructor(private val timeoutMs: Long, private val customLogger: Logger?) {
    actual fun getClient(): HttpClient {
        return createClient(timeoutMs, customLogger)
    }
}
