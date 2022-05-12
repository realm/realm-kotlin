package io.realm.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl
import io.ktor.client.features.logging.Logger

/**
 * Cache HttpClient on macOS.
 */
internal actual class HttpClientCache actual constructor(private val timeoutMs: Long, private val customLogger: Logger?) {
    private val client: HttpClient by lazy { createClient(timeoutMs, customLogger) }

    actual fun getClient(): HttpClient {
        return client
    }
    actual fun close() {
        client.close()
    }
}

public actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Curl, block)
}
