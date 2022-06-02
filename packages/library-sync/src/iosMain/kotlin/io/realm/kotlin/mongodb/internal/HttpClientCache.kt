package io.realm.kotlin.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.ios.Ios
import io.ktor.client.features.logging.Logger

/**
 * Cache HttpClient on iOS.
 */
internal actual class HttpClientCache actual constructor(timeoutMs: Long, customLogger: Logger?) {
    private val client: HttpClient by lazy { createClient(timeoutMs, customLogger) }
    actual fun getClient(): HttpClient {
        return client
    }
    actual fun close() {
        client.close()
    }
}

public actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Ios, block)
}
