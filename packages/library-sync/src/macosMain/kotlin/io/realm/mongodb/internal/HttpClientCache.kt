package io.realm.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.logging.Logger

/**
 * On MacOS we need to re-create the Client on each request due to
 * https://github.com/realm/realm-kotlin/issues/480.
 */
internal actual class HttpClientCache actual constructor(private val timeoutMs: Long, private val customLogger: Logger?) {
    actual fun getClient(): HttpClient {
        return createClient(timeoutMs, customLogger)
    }
}

public actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Curl, block)
}
