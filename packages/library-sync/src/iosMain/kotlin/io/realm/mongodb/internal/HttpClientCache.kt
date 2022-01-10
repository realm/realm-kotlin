package io.realm.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.ios.Ios
import io.ktor.client.features.logging.Logger

/**
 * Cache HttpClient on iOS.
 * https://github.com/realm/realm-kotlin/issues/480 only seem to be a problem on macOS.
 */
actual class HttpClientCache actual constructor(timeoutMs: Long, customLogger: Logger?) {
    private val client = createClient(timeoutMs, customLogger)
    actual fun getClient(): HttpClient {
        return client
    }
}

actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit ): HttpClient {
    return HttpClient(Ios, block)
}
